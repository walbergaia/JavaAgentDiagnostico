package agent.models;

import java.util.Map;
import java.util.HashMap;

/**
 * Representa uma única consulta SQL capturada pelo agente com informações aprimoradas.
 */
public class SqlQueryInfo {
    // Informações básicas
    public String timestamp;
    public String query;
    public String queryHash; // MD5 hash da query normalizada para agrupamento
    public String normalizedQuery; // Query sem parâmetros para pattern recognition
    public long durationMs;
    public String error;
    public String threadName;
    public boolean slow;
    public String queryType; // SELECT, INSERT, UPDATE, DELETE, etc.
    
    // Informações de trace correlation (futuro distributed tracing)
    public String traceId;
    public String spanId;
    public String parentSpanId;
    
    // Métricas de performance
    public int rowsAffected = -1; // -1 indica não disponível
    public long fetchSize = -1;
    public long queryPlanningTimeMs = -1;
    public long queryExecutionTimeMs = -1;
    
    // Informações de conexão
    public String connectionUrl;
    public String databaseName;
    public String connectionPoolName;
    public int activeConnections = -1;
    public int poolSize = -1;
    
    // Contexto da aplicação
    public String className;
    public String methodName;
    public int lineNumber = -1;
    
    // Métricas de cache/repetição
    public boolean fromPreparedStatementCache = false;
    public int executionCount = 1; // Quantas vezes esta query (hash) foi executada
    public long maxDurationMs = -1;
    public long minDurationMs = -1;
    public long totalDurationMs = -1; // para cálculo de média em agregação
    public long avgDurationMs = -1;
    
    // Tags customizadas para categorização
    public Map<String, String> tags = new HashMap<>();
    
    // Performance insights
    public String performanceInsight; // "FULL_TABLE_SCAN", "MISSING_INDEX", etc.
    
    public SqlQueryInfo() {
        this.tags = new HashMap<>();
    }
}