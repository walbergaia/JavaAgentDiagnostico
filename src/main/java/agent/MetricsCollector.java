package agent;

import agent.models.*;
import agent.sql.ConnectionPoolMonitor;
import agent.tracing.TracingManager;
import agent.sql.SqlAggregationRegistry;
import agent.io.IoFileTracker;
import agent.io.IoSocketTracker;
import agent.models.IoMetrics;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;

import java.lang.management.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;

/**
 * Coleta métricas da JVM e do sistema operacional.
 */
public class MetricsCollector {

    private final ConfigLoader config;
    private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();

    // OSHI para métricas de SO mais detalhadas
    private final SystemInfo systemInfo = new SystemInfo();
    private final CentralProcessor processor = systemInfo.getHardware().getProcessor();
    private final GlobalMemory memory = systemInfo.getHardware().getMemory();

    // Para calcular a carga da CPU, precisamos de leituras anteriores
    private long[] oldTicks;
    
    // Monitor de connection pools
    private final ConnectionPoolMonitor poolMonitor;

    public MetricsCollector(ConfigLoader config) {
        this.config = config;
        // Inicializa os ticks para o cálculo da CPU
        this.oldTicks = processor.getSystemCpuLoadTicks();
        // Inicializa monitor de connection pools
        this.poolMonitor = new ConnectionPoolMonitor();
    }

    /**
     * Coleta um snapshot de todas as métricas configuradas.
     * @return Um objeto AgentMetrics preenchido com os dados atuais.
     */
    public AgentMetrics collect() {
        AgentMetrics metrics = new AgentMetrics();

        // Metadados básicos
        metrics.timestamp = Instant.now().toString();
        metrics.agentName = config.getAgentName();
        metrics.application = config.getApplicationName();
        metrics.uptime = runtimeMXBean.getUptime();
        try {
            metrics.hostname = InetAddress.getLocalHost().getHostName();
            metrics.ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            metrics.hostname = "unknown";
            metrics.ip = "unknown";
        }

        // Coleta de métricas individuais
        metrics.heap = collectHeapMetrics();
        metrics.threads = collectThreadMetrics();

        if (config.isSystemCpuMemEnabled()) {
            metrics.cpu = collectCpuMetrics();
            metrics.memory = collectMemoryMetrics();
        }

        // Coleta GC, Exceções e SQL se habilitados
        if (config.isGcMetricsEnabled()) {
            metrics.gc = collectGcMetrics();
        }
        
        // Coleta exceções capturadas
        if (config.isExceptionCaptureEnabled()) {
            collectExceptions(metrics);
        }
        
        // Coleta queries SQL capturadas
        if (config.isSqlCaptureEnabled()) {
            collectSqlQueries(metrics);
            // Agregados
            metrics.sql.addAll(SqlAggregationRegistry.get().drainAggregated(config));
        }

        // Snapshot de CPU profiling
        if (config.isCpuProfilingEnabled()) {
            try {
                agent.profiling.CpuSamplingProfiler profiler = AgentMain.getCpuProfiler();
                if (profiler != null) {
                    metrics.cpuProfile = profiler.buildAndResetSnapshot();
                }
            } catch (Exception e) {
                System.err.println("AVISO: Falha ao coletar snapshot CPU: " + e.getMessage());
            }
        }

        // I/O metrics
        if (config.isFileIoEnabled() || config.isSocketIoEnabled()) {
            metrics.io = collectIoMetrics();
        }

            // Coleta spans de tracing
            if (config.isTracingEnabled()) {
                TracingManager.get().injectCollected(metrics);
            }
        
        // Coleta métricas de connection pools
        collectConnectionPoolMetrics(metrics);

        return metrics;
    }

    private HeapMetrics collectHeapMetrics() {
        HeapMetrics heap = new HeapMetrics();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        heap.used = heapUsage.getUsed();
        heap.max = heapUsage.getMax();
        heap.committed = heapUsage.getCommitted();
        return heap;
    }

    private ThreadStats collectThreadMetrics() {
        ThreadStats stats = new ThreadStats();
        stats.total = threadMXBean.getThreadCount();
        
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
        stats.deadlocks = (deadlockedThreads != null) ? deadlockedThreads.length : 0;

        // Classifica threads por estado
        long[] allThreadIds = threadMXBean.getAllThreadIds();
        ThreadInfo[] threadInfos;
        
        // Se análise de stack está habilitada, obtém stack traces completos
        if (config.isThreadStackAnalysisEnabled()) {
            int maxDepth = config.getThreadStackMaxDepth();
            threadInfos = threadMXBean.getThreadInfo(allThreadIds, maxDepth);
        } else {
            threadInfos = threadMXBean.getThreadInfo(allThreadIds);
        }
        
        // Contadores e listas para cada estado
        int runnableCount = 0, blockedCount = 0, waitingCount = 0, timedWaitingCount = 0;
        int sampleSize = config.getThreadStackSampleSize();
        
        for (ThreadInfo info : threadInfos) {
            if (info != null) {
                Thread.State state = info.getThreadState();
                
                switch (state) {
                    case RUNNABLE: 
                        stats.runnable++; 
                        if (config.isThreadStackAnalysisEnabled() && runnableCount < sampleSize) {
                            stats.runnableDetails.add(createThreadStackInfo(info));
                            runnableCount++;
                        }
                        break;
                    case BLOCKED: 
                        stats.blocked++; 
                        if (config.isThreadStackAnalysisEnabled() && blockedCount < sampleSize) {
                            stats.blockedDetails.add(createThreadStackInfo(info));
                            blockedCount++;
                        }
                        break;
                    case WAITING: 
                        stats.waiting++; 
                        if (config.isThreadStackAnalysisEnabled() && waitingCount < sampleSize) {
                            stats.waitingDetails.add(createThreadStackInfo(info));
                            waitingCount++;
                        }
                        break;
                    case TIMED_WAITING: 
                        stats.timed_waiting++; 
                        if (config.isThreadStackAnalysisEnabled() && timedWaitingCount < sampleSize) {
                            stats.timedWaitingDetails.add(createThreadStackInfo(info));
                            timedWaitingCount++;
                        }
                        break;
                    default: break;
                }
            }
        }
        
        // Coleta detalhes de threads em deadlock se habilitado
        if (config.isThreadStackAnalysisEnabled() && deadlockedThreads != null) {
            ThreadInfo[] deadlockedInfos = threadMXBean.getThreadInfo(deadlockedThreads, config.getThreadStackMaxDepth());
            for (ThreadInfo info : deadlockedInfos) {
                if (info != null && stats.deadlockedDetails.size() < sampleSize) {
                    stats.deadlockedDetails.add(createThreadStackInfo(info));
                }
            }
        }
        
        return stats;
    }

    private CpuMetrics collectCpuMetrics() {
        CpuMetrics cpu = new CpuMetrics();
        // getProcessCpuLoad() pode retornar < 0, então tratamos isso.
        double processCpu = osMXBean.getSystemLoadAverage(); // More reliable than getProcessCpuLoad() across platforms
        cpu.process = (processCpu >= 0) ? processCpu : 0.0;

        // Uso de CPU do sistema via OSHI (mais preciso)
        cpu.system = processor.getSystemCpuLoadBetweenTicks(oldTicks);
        this.oldTicks = processor.getSystemCpuLoadTicks(); // Atualiza para a próxima medição
        
        // Garante que o valor esteja entre 0 e 1
        cpu.system = Math.max(0.0, Math.min(1.0, cpu.system));

        return cpu;
    }

    private MemoryMetrics collectMemoryMetrics() {
        MemoryMetrics mem = new MemoryMetrics();
        mem.total = memory.getTotal();
        mem.free = memory.getAvailable();
        return mem;
    }

    private GcMetrics collectGcMetrics() {
        GcMetrics gc = new GcMetrics();
        gc.name = "Combined"; // Agregado de todos os GCs
        
        // Obtém beans de Garbage Collection
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gc.collectionCount += gcBean.getCollectionCount();
            gc.collectionTimeMs += gcBean.getCollectionTime();
        }
        
        return gc;
    }
    
    private void collectExceptions(AgentMetrics metrics) {
        try {
            // Obtém ExceptionHandler do AgentMain
            ExceptionHandler exceptionHandler = AgentMain.getExceptionHandler();
            if (exceptionHandler != null) {
                java.util.List<agent.models.ExceptionInfo> drained = exceptionHandler.drainExceptions();
                if (drained.isEmpty()) {
                    // Debug opcional
                    // System.out.println("DEBUG: Nenhuma exceção capturada neste intervalo.");
                }
                metrics.exceptions.addAll(drained);
            }
        } catch (Exception e) {
            System.err.println("AVISO: Erro ao coletar exceções: " + e.getMessage());
        }
    }
    
    private void collectSqlQueries(AgentMetrics metrics) {
        try {
            // Obtém queries do SqlRegistry
            java.util.List<agent.models.SqlQueryInfo> drained = SqlRegistry.getInstance().drainQueries();
            if (drained.isEmpty()) {
                // System.out.println("DEBUG: Nenhuma query SQL capturada neste intervalo.");
            }
            metrics.sql.addAll(drained);
        } catch (Exception e) {
            System.err.println("AVISO: Erro ao coletar queries SQL: " + e.getMessage());
        }
    }
    
    private void collectConnectionPoolMetrics(AgentMetrics metrics) {
        try {
            metrics.connectionPools.addAll(poolMonitor.collectAllPoolMetrics());
        } catch (Exception e) {
            System.err.println("AVISO: Erro ao coletar métricas de connection pool: " + e.getMessage());
        }
    }
    
    private ThreadStackInfo createThreadStackInfo(ThreadInfo threadInfo) {
        ThreadStackInfo stackInfo = new ThreadStackInfo();
        
        stackInfo.threadId = threadInfo.getThreadId();
        stackInfo.threadName = threadInfo.getThreadName();
        stackInfo.threadState = threadInfo.getThreadState().toString();
        stackInfo.blockedTime = threadInfo.getBlockedTime();
        stackInfo.waitedTime = threadInfo.getWaitedTime();
        stackInfo.inNative = threadInfo.isInNative();
        stackInfo.suspended = threadInfo.isSuspended();
        stackInfo.timestamp = java.time.Instant.now().toString();
        
        // Informações de lock
        if (threadInfo.getLockName() != null) {
            stackInfo.lockName = threadInfo.getLockName();
        }
        if (threadInfo.getLockOwnerName() != null) {
            stackInfo.lockOwner = threadInfo.getLockOwnerName();
        }
        
        // Captura stack trace
        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        stackInfo.stackTrace = new java.util.ArrayList<>();
        
        if (stackTrace != null) {
            for (StackTraceElement element : stackTrace) {
                stackInfo.stackTrace.add(element.toString());
            }
        }
        
        return stackInfo;
    }

    private IoMetrics collectIoMetrics() {
        IoMetrics io = new IoMetrics();
        int limit = config.getIoMaxTopEntries();
        long[] totals = new long[2];
        if (config.isFileIoEnabled()) {
            io.topFiles = IoFileTracker.get().top(limit, totals);
            io.totalFileReadBytes = totals[0];
            io.totalFileWriteBytes = totals[1];
        }
        totals = new long[2];
        if (config.isSocketIoEnabled()) {
            io.topSockets = IoSocketTracker.get().top(limit, totals);
            io.totalSocketReadBytes = totals[0];
            io.totalSocketWriteBytes = totals[1];
        }
        return io;
    }
}