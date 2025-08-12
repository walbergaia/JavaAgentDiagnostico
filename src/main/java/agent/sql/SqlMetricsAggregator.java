package agent.sql;

import agent.models.SqlQueryInfo;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agregador de métricas SQL que calcula estatísticas e ordena queries por impacto.
 */
public class SqlMetricsAggregator {
    
    // Mapa de queries agregadas por hash
    private final Map<String, AggregatedSqlMetrics> aggregatedMetrics = new ConcurrentHashMap<>();
    
    // Tempo total do sistema para cálculo de percentuais
    private long totalSystemTimeMs = 0;
    
    /**
     * Adiciona uma query para agregação
     */
    public void addQuery(SqlQueryInfo query) {
        if (query == null || query.queryHash == null) {
            return;
        }
        
        aggregatedMetrics.compute(query.queryHash, (hash, existing) -> {
            if (existing == null) {
                return new AggregatedSqlMetrics(query);
            } else {
                existing.addExecution(query);
                return existing;
            }
        });
        
        // Atualiza tempo total do sistema
        totalSystemTimeMs += query.durationMs;
    }
    
    /**
     * Obtém as queries ordenadas por impacto (tempo total consumido)
     */
    public List<AggregatedSqlMetrics> getQueriesByImpact() {
        // Calcula percentuais
        aggregatedMetrics.values().forEach(metrics -> 
            metrics.calculatePercentage(totalSystemTimeMs)
        );
        
        // Ordena por tempo total (maior impacto primeiro)
        List<AggregatedSqlMetrics> sorted = aggregatedMetrics.values().stream()
            .sorted((a, b) -> Long.compare(b.totalTimeMs, a.totalTimeMs))
            .collect(Collectors.toList());
        
        // Define ranking de impacto
        int rank = 1;
        for (AggregatedSqlMetrics metrics : sorted) {
            metrics.impactRank = rank++;
        }
        
        return sorted;
    }
    
    /**
     * Obtém queries bloqueadas (threads que ficaram BLOCKED)
     */
    public List<AggregatedSqlMetrics> getBlockedQueries() {
        return aggregatedMetrics.values().stream()
            .filter(m -> m.blockedExecutions > 0)
            .sorted((a, b) -> Integer.compare(b.blockedExecutions, a.blockedExecutions))
            .collect(Collectors.toList());
    }
    
    /**
     * Obtém queries lentas
     */
    public List<AggregatedSqlMetrics> getSlowQueries(long thresholdMs) {
        return aggregatedMetrics.values().stream()
            .filter(m -> m.maxDurationMs > thresholdMs)
            .sorted((a, b) -> Long.compare(b.maxDurationMs, a.maxDurationMs))
            .collect(Collectors.toList());
    }
    
    /**
     * Gera relatório formatado das métricas
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n========================================\n");
        report.append("     SQL PERFORMANCE ANALYSIS REPORT     \n");
        report.append("========================================\n\n");
        
        // Resumo geral
        report.append("SUMMARY\n");
        report.append("-------\n");
        report.append(String.format("Total Queries Tracked: %d\n", aggregatedMetrics.size()));
        report.append(String.format("Total Executions: %d\n", 
            aggregatedMetrics.values().stream().mapToInt(m -> m.executionCount).sum()));
        report.append(String.format("Total Time: %,d ms\n", totalSystemTimeMs));
        report.append("\n");
        
        // Top 10 queries por impacto
        report.append("TOP QUERIES BY IMPACT\n");
        report.append("--------------------\n");
        List<AggregatedSqlMetrics> topQueries = getQueriesByImpact();
        int count = 0;
        for (AggregatedSqlMetrics metrics : topQueries) {
            if (++count > 10) break;
            
            report.append(String.format("\n#%d - %.2f%% of total time\n", 
                metrics.impactRank, metrics.percentageOfTotal));
            report.append(String.format("  Query Type: %s\n", metrics.queryType));
            report.append(String.format("  Query: %s\n", truncate(metrics.normalizedQuery, 100)));
            report.append(String.format("  Executions: %d\n", metrics.executionCount));
            report.append(String.format("  Total Time: %,d ms\n", metrics.totalTimeMs));
            report.append(String.format("  Avg Time: %,d ms\n", metrics.avgDurationMs));
            report.append(String.format("  Min/Max: %d ms / %,d ms\n", 
                metrics.minDurationMs, metrics.maxDurationMs));
            
            if (metrics.blockedExecutions > 0) {
                report.append(String.format("  ⚠️ BLOCKED: %d times\n", metrics.blockedExecutions));
            }
            
            if (metrics.errorCount > 0) {
                report.append(String.format("  ❌ ERRORS: %d\n", metrics.errorCount));
            }
            
            if (metrics.performanceInsights.size() > 0) {
                report.append(String.format("  ⚡ Insights: %s\n", 
                    String.join(", ", metrics.performanceInsights)));
            }
        }
        
        // Queries com threads bloqueadas
        List<AggregatedSqlMetrics> blockedQueries = getBlockedQueries();
        if (!blockedQueries.isEmpty()) {
            report.append("\n\nBLOCKED THREAD QUERIES\n");
            report.append("----------------------\n");
            for (AggregatedSqlMetrics metrics : blockedQueries) {
                report.append(String.format("\n  Query: %s\n", truncate(metrics.normalizedQuery, 100)));
                report.append(String.format("  Blocked Count: %d / %d executions\n", 
                    metrics.blockedExecutions, metrics.executionCount));
                report.append(String.format("  Threads: %s\n", 
                    String.join(", ", metrics.affectedThreads)));
            }
        }
        
        // Estatísticas por tipo de query
        report.append("\n\nSTATISTICS BY QUERY TYPE\n");
        report.append("------------------------\n");
        Map<String, List<AggregatedSqlMetrics>> byType = aggregatedMetrics.values().stream()
            .collect(Collectors.groupingBy(m -> m.queryType));
        
        for (Map.Entry<String, List<AggregatedSqlMetrics>> entry : byType.entrySet()) {
            String type = entry.getKey();
            List<AggregatedSqlMetrics> queries = entry.getValue();
            
            long totalTime = queries.stream().mapToLong(m -> m.totalTimeMs).sum();
            int totalExec = queries.stream().mapToInt(m -> m.executionCount).sum();
            
            report.append(String.format("\n%s:\n", type));
            report.append(String.format("  Unique Queries: %d\n", queries.size()));
            report.append(String.format("  Total Executions: %d\n", totalExec));
            report.append(String.format("  Total Time: %,d ms (%.2f%%)\n", 
                totalTime, (totalTime * 100.0) / totalSystemTimeMs));
        }
        
        report.append("\n========================================\n");
        
        return report.toString();
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
    
    /**
     * Classe interna para métricas agregadas
     */
    public static class AggregatedSqlMetrics {
        // Identificação
        public String queryHash;
        public String normalizedQuery;
        public String queryType;
        public String eventType;
        
        // Estatísticas de execução
        public int executionCount = 0;
        public long totalTimeMs = 0;
        public long avgDurationMs = 0;
        public long maxDurationMs = 0;
        public long minDurationMs = Long.MAX_VALUE;
        public double percentageOfTotal = 0.0;
        public int impactRank = 0;
        
        // Estatísticas de problemas
        public int blockedExecutions = 0;
        public int slowExecutions = 0;
        public int errorCount = 0;
        
        // Informações de contexto
        public Set<String> affectedThreads = new HashSet<>();
        public Set<String> connectionIds = new HashSet<>();
        public Set<String> performanceInsights = new HashSet<>();
        public Map<String, Integer> errorTypes = new HashMap<>();
        
        // Informações da primeira execução
        public String firstExecutionTime;
        public String lastExecutionTime;
        public String connectionUrl;
        public String databaseName;
        public String connectionPoolName;
        
        // Contexto da aplicação
        public Set<String> callingClasses = new HashSet<>();
        public Set<String> callingMethods = new HashSet<>();
        
        public AggregatedSqlMetrics(SqlQueryInfo query) {
            this.queryHash = query.queryHash;
            this.normalizedQuery = query.normalizedQuery;
            this.queryType = query.queryType;
            this.eventType = query.eventType;
            this.firstExecutionTime = query.startTime;
            this.connectionUrl = query.connectionUrl;
            this.databaseName = query.databaseName;
            this.connectionPoolName = query.connectionPoolName;
            
            addExecution(query);
        }
        
        public void addExecution(SqlQueryInfo query) {
            executionCount++;
            totalTimeMs += query.durationMs;
            avgDurationMs = totalTimeMs / executionCount;
            maxDurationMs = Math.max(maxDurationMs, query.durationMs);
            minDurationMs = Math.min(minDurationMs, query.durationMs);
            
            lastExecutionTime = query.startTime;
            
            // Tracking de threads
            if (query.threadName != null) {
                affectedThreads.add(query.threadName);
            }
            
            // Tracking de conexões
            if (query.connectionId != null) {
                connectionIds.add(query.connectionId);
            }
            
            // Tracking de problemas
            if (query.threadBlocked) {
                blockedExecutions++;
            }
            
            if (query.slow) {
                slowExecutions++;
            }
            
            if (query.error != null) {
                errorCount++;
                String errorType = query.error.split(":")[0];
                errorTypes.merge(errorType, 1, Integer::sum);
            }
            
            // Performance insights
            if (query.performanceInsight != null) {
                performanceInsights.add(query.performanceInsight);
            }
            
            // Contexto da aplicação
            if (query.className != null) {
                callingClasses.add(query.className);
            }
            
            if (query.methodName != null) {
                callingMethods.add(query.methodName);
            }
        }
        
        public void calculatePercentage(long totalSystemTime) {
            if (totalSystemTime > 0) {
                percentageOfTotal = (totalTimeMs * 100.0) / totalSystemTime;
            }
        }
    }
    
    /**
     * Limpa as métricas agregadas
     */
    public void clear() {
        aggregatedMetrics.clear();
        totalSystemTimeMs = 0;
    }
    
    /**
     * Obtém o número de queries únicas rastreadas
     */
    public int getUniqueQueryCount() {
        return aggregatedMetrics.size();
    }
    
    /**
     * Obtém o tempo total do sistema
     */
    public long getTotalSystemTimeMs() {
        return totalSystemTimeMs;
    }
}
