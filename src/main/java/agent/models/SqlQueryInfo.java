package agent.models;

import java.util.Map;
import java.util.HashMap;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Representa uma execução de instrução SQL capturada pelo agente.
 * Inclui informações detalhadas sobre execução de prepared statements.
 */
public class SqlQueryInfo {
    // === Informações de Execução ===
    
    // Horário de início (Start Time) - data e hora exata da execução
    public String startTime; // ISO-8601 format
    public long startTimeMillis; // timestamp em millis para cálculos
    
    // Tipo de evento
    public String eventType = "PREPARED_STATEMENT_EXECUTION"; // ou "STATEMENT_EXECUTION", "BATCH_EXECUTION"
    
    // Duração - tempo total que a execução levou
    public long durationMicros; // em microssegundos para maior precisão
    public long durationMs; // em milissegundos para compatibilidade
    
    // Connection ID - identificador da conexão JDBC utilizada
    public String connectionId;
    public int connectionHashCode; // hashCode da conexão para tracking
    
    // Descrição - SQL executado
    public String query; // SQL completo com parâmetros
    public String normalizedQuery; // SQL com placeholders (?)
    public String queryWithParams; // SQL com valores reais dos parâmetros
    
    // Thread - informações da thread executora
    public String threadName; // ex: "Nyx-FileSpooler-3 [main]"
    public long threadId;
    public String threadState; // RUNNABLE, BLOCKED, WAITING, etc.
    public boolean threadBlocked; // true se thread está BLOCKED
    
    // === Métricas de Agregação ===
    
    // Estatísticas por instrução SQL
    public String queryHash; // MD5 hash da query normalizada para agrupamento
    public int executionCount = 1; // Quantidade de execuções
    public long totalTimeMs = 0; // Tempo total gasto (soma de todas execuções)
    public double percentageOfTotal = 0.0; // Percentual do tempo total
    public long avgDurationMs = 0; // Tempo médio por execução
    public long maxDurationMs = 0; // Tempo máximo registrado
    public long minDurationMs = Long.MAX_VALUE; // Tempo mínimo registrado
    
    // Ordem de impacto
    public int impactRank = 0; // 1 = query mais custosa, 2 = segunda mais custosa, etc.
    
    // === Informações Detalhadas ===
    
    // Tipo de query
    public String queryType; // SELECT, INSERT, UPDATE, DELETE, CALL, etc.
    
    // Métricas de performance
    public int rowsAffected = -1; // Linhas afetadas (UPDATE/DELETE/INSERT)
    public int rowsFetched = -1; // Linhas retornadas (SELECT)
    public long fetchSize = -1; // Tamanho do fetch configurado
    
    // Informações de conexão e pool
    public String connectionUrl;
    public String databaseName;
    public String connectionPoolName;
    public int activeConnections = -1;
    public int poolSize = -1;
    public long connectionAcquireTimeMs = -1; // Tempo para obter conexão do pool
    
    // Contexto da aplicação
    public String className; // Classe que executou a query
    public String methodName; // Método que executou a query
    public int lineNumber = -1; // Linha de código
    public String stackTrace; // Stack trace resumido
    
    // Prepared Statement específico
    public boolean isPreparedStatement = false;
    public boolean fromPreparedStatementCache = false;
    public String preparedStatementId; // ID único do prepared statement
    public Map<Integer, Object> parameters; // Parâmetros do prepared statement
    
    // Performance insights e alertas
    public boolean slow = false; // Query lenta (acima do threshold)
    public String performanceInsight; // "FULL_TABLE_SCAN", "MISSING_INDEX", etc.
    public String error; // Erro se houver
    
    // Trace correlation (para distributed tracing futuro)
    public String traceId;
    public String spanId;
    public String parentSpanId;
    
    // Tags customizadas para categorização
    public Map<String, String> tags = new HashMap<>();
    
    // Métodos auxiliares
    
    public SqlQueryInfo() {
        this.tags = new HashMap<>();
        this.parameters = new HashMap<>();
        this.startTimeMillis = System.currentTimeMillis();
        this.startTime = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(startTimeMillis));
    }
    
    /**
     * Atualiza as métricas de agregação quando múltiplas execuções são combinadas
     */
    public void updateAggregateMetrics(long duration) {
        executionCount++;
        totalTimeMs += duration;
        avgDurationMs = totalTimeMs / executionCount;
        maxDurationMs = Math.max(maxDurationMs, duration);
        minDurationMs = Math.min(minDurationMs, duration);
    }
    
    /**
     * Calcula o percentual do tempo total
     */
    public void calculatePercentage(long totalSystemTime) {
        if (totalSystemTime > 0) {
            percentageOfTotal = (totalTimeMs * 100.0) / totalSystemTime;
        }
    }
    
    /**
     * Define se a thread está bloqueada baseado no estado
     */
    public void updateThreadState(Thread.State state) {
        this.threadState = state.toString();
        this.threadBlocked = (state == Thread.State.BLOCKED);
    }
    
    /**
     * Converte duração de microssegundos para milissegundos
     */
    public void setDurationMicros(long micros) {
        this.durationMicros = micros;
        this.durationMs = micros / 1000;
    }
}
