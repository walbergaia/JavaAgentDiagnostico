package agent;

import agent.models.SqlQueryInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Um registro central e thread-safe para armazenar informações de queries SQL
 * coletadas pela instrumentação.
 */
public class SqlRegistry {

    private static final SqlRegistry INSTANCE = new SqlRegistry();
    private final List<SqlQueryInfo> capturedQueries = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, AtomicInteger> queryExecutionCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> normalizedQueryCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHED_NORMALIZED_QUERIES = 5000;

    private SqlRegistry() {}

    public static SqlRegistry getInstance() {
        return INSTANCE;
    }

    public void add(SqlQueryInfo info) {
        capturedQueries.add(info);
        // Atualiza estatísticas de execução
        if (info != null && info.queryHash != null) {
            queryExecutionCounts.computeIfAbsent(info.queryHash, k -> new AtomicInteger()).incrementAndGet();
            // Registro de agregação
            agent.sql.SqlAggregationRegistry.get().record(info, agent.ConfigLoader.getInstance());
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
    }
}