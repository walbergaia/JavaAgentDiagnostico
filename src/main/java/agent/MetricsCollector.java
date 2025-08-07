package agent;

import agent.models.*;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;

import java.lang.management.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Arrays;

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
    private long oldProcessorCpuLoadTicks;

    public MetricsCollector(ConfigLoader config) {
        this.config = config;
        // Inicializa os ticks para o cálculo da CPU
        this.oldTicks = new long[CentralProcessor.TickType.values().length];
        this.oldProcessorCpuLoadTicks = processor.getProcessorCpuLoadTicks();
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

        // Métricas de GC, Exceções e SQL serão adicionadas aqui posteriormente.

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
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(allThreadIds);
        
        for (ThreadInfo info : threadInfos) {
            if (info != null) {
                switch (info.getThreadState()) {
                    case RUNNABLE: stats.runnable++; break;
                    case BLOCKED: stats.blocked++; break;
                    case WAITING: stats.waiting++; break;
                    case TIMED_WAITING: stats.timed_waiting++; break;
                    default: break;
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
}