package agent;

import agent.models.SqlQueryInfo;
import agent.sql.SqlMetricsAggregator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Um registro central e thread-safe para armazenar informações de queries SQL
 * coletadas pela instrumentação. Inclui agregação de métricas e geração de relatórios.
 */
public class SqlRegistry {

    private static final SqlRegistry INSTANCE = new SqlRegistry();
    private final List<SqlQueryInfo> capturedQueries = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, AtomicInteger> queryExecutionCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> normalizedQueryCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHED_NORMALIZED_QUERIES = 5000;
    
    // Agregador de métricas SQL
    private final SqlMetricsAggregator metricsAggregator = new SqlMetricsAggregator();
    
    // Scheduler para relatórios periódicos
    private final ScheduledExecutorService reportScheduler = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "SQL-Metrics-Reporter");
            t.setDaemon(true);
            return t;
        }
    );
    
    // Contadores globais
    private final AtomicLong totalQueriesProcessed = new AtomicLong(0);
    private final AtomicLong totalTimeSpentMs = new AtomicLong(0);
    private final AtomicInteger blockedQueriesCount = new AtomicInteger(0);
    private final AtomicInteger slowQueriesCount = new AtomicInteger(0);
    private final AtomicInteger errorQueriesCount = new AtomicInteger(0);
    
    // Configuração de relatórios
    private volatile boolean reportingEnabled = true;
    private volatile long reportIntervalMinutes = 5; // Relatório a cada 5 minutos por padrão

    private SqlRegistry() {
        // Inicia geração de relatórios periódicos
        startPeriodicReporting();
    }

    public static SqlRegistry getInstance() {
        return INSTANCE;
    }

    public void add(SqlQueryInfo info) {
        if (info == null) return;
        
        capturedQueries.add(info);
        
        // Adiciona ao agregador de métricas
        metricsAggregator.addQuery(info);
        
        // Atualiza estatísticas de execução
        if (info.queryHash != null) {
            queryExecutionCounts.computeIfAbsent(info.queryHash, k -> new AtomicInteger()).incrementAndGet();
            
            // Atualiza contadores globais
            totalQueriesProcessed.incrementAndGet();
            totalTimeSpentMs.addAndGet(info.durationMs);
            
            if (info.threadBlocked) {
                blockedQueriesCount.incrementAndGet();
            }
            
            if (info.slow) {
                slowQueriesCount.incrementAndGet();
            }
            
            if (info.error != null) {
                errorQueriesCount.incrementAndGet();
            }
            
            // Registro de agregação (mantém compatibilidade)
            try {
                agent.sql.SqlAggregationRegistry.get().record(info, agent.ConfigLoader.getInstance());
            } catch (Exception e) {
                // Ignora erros para não afetar o fluxo principal
            }
        }
        
        // Log de queries críticas em tempo real
        if (info.threadBlocked || (info.slow && info.durationMs > 5000)) {
            logCriticalQuery(info);
        }
    }

    /**
     * Coleta as queries capturadas desde a última chamada e limpa a lista.
     * @return Uma lista de queries SQL.
     */
    public List<SqlQueryInfo> drainQueries() {
        if (capturedQueries.isEmpty()) {
            return new ArrayList<>();
        }
        List<SqlQueryInfo> drained = new ArrayList<>(capturedQueries);
        capturedQueries.clear();
        return drained;
    }
    
    /**
     * Retorna estatísticas de execução das queries agrupadas por hash.
     * @return Mapa com hash da query e número de execuções
     */
    public Map<String, Integer> getQueryExecutionStats() {
        Map<String, Integer> stats = new java.util.HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : queryExecutionCounts.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().get());
        }
        return stats;
    }
    
    /**
     * Limpa estatísticas antigas para evitar vazamento de memória.
     */
    public void cleanupOldStats() {
        if (queryExecutionCounts.size() > MAX_CACHED_NORMALIZED_QUERIES) {
            // Remove 20% das entradas mais antigas (simplificado)
            int toRemove = queryExecutionCounts.size() / 5;
            List<String> keysToRemove = new ArrayList<>(queryExecutionCounts.keySet());
            
            for (int i = 0; i < toRemove && i < keysToRemove.size(); i++) {
                queryExecutionCounts.remove(keysToRemove.get(i));
            }
        }
        
        // Limpa cache de queries normalizadas se necessário
        if (normalizedQueryCache.size() > MAX_CACHED_NORMALIZED_QUERIES) {
            normalizedQueryCache.clear();
        }
    }
    
    /**
     * Retorna o número total de queries capturadas.
     */
    public int getTotalQueriesCount() {
        return capturedQueries.size();
    }
    
    /**
     * Retorna o número de tipos únicos de queries.
     */
    public int getUniqueQueryTypesCount() {
        return queryExecutionCounts.size();
    }
    
    /**
     * Limpa todas as queries e estatísticas (para testes ou reset).
     */
    public void clear() {
        capturedQueries.clear();
        queryExecutionCounts.clear();
        normalizedQueryCache.clear();
        metricsAggregator.clear();
        
        // Reset contadores
        totalQueriesProcessed.set(0);
        totalTimeSpentMs.set(0);
        blockedQueriesCount.set(0);
        slowQueriesCount.set(0);
        errorQueriesCount.set(0);
    }
    
    /**
     * Inicia a geração de relatórios periódicos
     */
    private void startPeriodicReporting() {
        reportScheduler.scheduleAtFixedRate(() -> {
            if (reportingEnabled) {
                try {
                    generateAndLogReport();
                } catch (Exception e) {
                    System.err.println("[SQL Registry] Error generating report: " + e.getMessage());
                }
            }
        }, reportIntervalMinutes, reportIntervalMinutes, TimeUnit.MINUTES);
    }
    
    /**
     * Gera e loga o relatório de métricas SQL
     */
    public void generateAndLogReport() {
        String report = metricsAggregator.generateReport();
        
        // Adiciona estatísticas globais ao relatório
        StringBuilder fullReport = new StringBuilder();
        fullReport.append("\n╔══════════════════════════════════════╗\n");
        fullReport.append("║    SQL METRICS - GLOBAL STATISTICS    ║\n");
        fullReport.append("╚══════════════════════════════════════╝\n");
        fullReport.append(String.format("Total Queries Processed: %,d\n", totalQueriesProcessed.get()));
        fullReport.append(String.format("Total Time Spent: %,d ms (%.2f seconds)\n", 
            totalTimeSpentMs.get(), totalTimeSpentMs.get() / 1000.0));
        fullReport.append(String.format("Blocked Queries: %,d\n", blockedQueriesCount.get()));
        fullReport.append(String.format("Slow Queries: %,d\n", slowQueriesCount.get()));
        fullReport.append(String.format("Failed Queries: %,d\n", errorQueriesCount.get()));
        
        if (totalQueriesProcessed.get() > 0) {
            fullReport.append(String.format("Average Query Time: %.2f ms\n", 
                (double) totalTimeSpentMs.get() / totalQueriesProcessed.get()));
        }
        
        fullReport.append(report);
        
        // Loga o relatório
        System.out.println(fullReport.toString());
        
        // Salva em arquivo se configurado
        saveReportToFile(fullReport.toString());
    }
    
    /**
     * Loga queries críticas em tempo real
     */
    private void logCriticalQuery(SqlQueryInfo info) {
        StringBuilder log = new StringBuilder();
        log.append("\n⚠️ [CRITICAL SQL DETECTED] ");
        
        if (info.threadBlocked) {
            log.append("THREAD BLOCKED | ");
        }
        
        if (info.slow) {
            log.append("SLOW QUERY | ");
        }
        
        log.append(String.format("Duration: %,d ms (%,d μs) | ", 
            info.durationMs, info.durationMicros));
        log.append(String.format("Thread: %s | ", info.threadName));
        log.append(String.format("Type: %s | ", info.queryType));
        log.append(String.format("Connection: %s\n", info.connectionId));
        log.append(String.format("Query: %s\n", 
            truncate(info.normalizedQuery, 200)));
        
        if (info.stackTrace != null && !info.stackTrace.isEmpty()) {
            log.append(String.format("Stack: %s\n", info.stackTrace));
        }
        
        System.err.println(log.toString());
    }
    
    /**
     * Salva relatório em arquivo (se configurado)
     */
    private void saveReportToFile(String report) {
        try {
            ConfigLoader config = ConfigLoader.getInstance();
            if (config.isLocalStorageEnabled()) {
                LocalStorageManager storage = LocalStorageManager.getInstance();
                if (storage != null) {
                    storage.saveData("sql-metrics-report", report);
                }
            }
        } catch (Exception e) {
            // Ignora erros de salvamento
        }
    }
    
    /**
     * Obtém o agregador de métricas
     */
    public SqlMetricsAggregator getMetricsAggregator() {
        return metricsAggregator;
    }
    
    /**
     * Configura o intervalo de geração de relatórios
     */
    public void setReportIntervalMinutes(long minutes) {
        this.reportIntervalMinutes = minutes;
    }
    
    /**
     * Habilita/desabilita geração de relatórios
     */
    public void setReportingEnabled(boolean enabled) {
        this.reportingEnabled = enabled;
    }
    
    /**
     * Gera relatório sob demanda
     */
    public String getReport() {
        return metricsAggregator.generateReport();
    }
    
    /**
     * Obtém estatísticas globais
     */
    public Map<String, Object> getGlobalStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalQueries", totalQueriesProcessed.get());
        stats.put("totalTimeMs", totalTimeSpentMs.get());
        stats.put("blockedQueries", blockedQueriesCount.get());
        stats.put("slowQueries", slowQueriesCount.get());
        stats.put("errorQueries", errorQueriesCount.get());
        stats.put("uniqueQueries", metricsAggregator.getUniqueQueryCount());
        
        if (totalQueriesProcessed.get() > 0) {
            stats.put("avgTimeMs", (double) totalTimeSpentMs.get() / totalQueriesProcessed.get());
        }
        
        return stats;
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
    
    /**
     * Shutdown do registry
     */
    public void shutdown() {
        reportScheduler.shutdown();
        try {
            if (!reportScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                reportScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            reportScheduler.shutdownNow();
        }
    }
}
