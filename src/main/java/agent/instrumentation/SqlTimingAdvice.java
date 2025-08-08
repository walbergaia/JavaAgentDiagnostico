package agent.instrumentation;

import agent.ConfigLoader;
import agent.SqlRegistry;
import agent.models.SqlQueryInfo;
import agent.sql.SqlQueryNormalizer;
import agent.sql.PreparedStatementRegistry;
import net.bytebuddy.asm.Advice;
import agent.tracing.TracingManager;
import agent.models.TraceSpan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O "Advice" do Byte Buddy que contém a lógica a ser injetada nos métodos de PreparedStatement.
 */
public class SqlTimingAdvice {

    // Pattern simples para extrair o tipo de query.
    private static final Pattern QUERY_TYPE_PATTERN = Pattern.compile("^\\s*(select|insert|update|delete|call|with)", Pattern.CASE_INSENSITIVE);

    /**
     * Executado na entrada do método instrumentado.
     * @return O tempo de início em nanossegundos.
     */
    @Advice.OnMethodEnter
    public static long onEnter() {
        return System.nanoTime();
    }

    /**
     * Executado na saída do método instrumentado - versão aprimorada.
     * @param startTime O valor retornado pelo método onEnter.
     * @param statement O objeto PreparedStatement (`this` no método original).
     * @param thrown A exceção lançada pelo método, se houver.
     * @param result O resultado do método (para queries que retornam ResultSet).
     */
    @Advice.OnMethodExit(onThrowable = SQLException.class)
    public static void onExit(@Advice.Enter long startTime,
                              @Advice.This PreparedStatement statement,
                              @Advice.Thrown SQLException thrown,
                              @Advice.Return Object result) {

        try {
            long durationNs = System.nanoTime() - startTime;
            long durationMs = durationNs / 1_000_000;

            // Se a query for mais rápida que o threshold e não deu erro, ainda registra mas marca como não-slow
            // Apenas ignora queries sub-milissegundo que não tiveram erro (muito rápidas para serem relevantes)
            if (thrown == null && durationMs < 1) { 
                return;
            }

            SqlQueryInfo info = createEnhancedSqlInfo(statement, startTime, durationMs, thrown, result);
            // rowsAffected se possível (executeUpdate retorna int)
            if (result instanceof Integer) {
                try { info.rowsAffected = (Integer) result; } catch (Exception ignored) {}
            }
            
            // Log slow queries para debugging
            if (info.slow) {
                System.out.println("SLOW SQL (" + durationMs + "ms): " + info.queryType + " - " + 
                    (info.query != null && info.query.length() > 50 ? info.query.substring(0, 50) + "..." : info.query));
            }

            SqlRegistry.getInstance().add(info);
        } catch (Throwable t) {
            // Silencia erros da instrumentação para não quebrar a aplicação principal
            System.err.println("AVISO: Erro na instrumentação SQL (não afeta a aplicação): " + t.getMessage());
        }
    }
    
    /**
     * Cria um objeto SqlQueryInfo aprimorado com todas as métricas disponíveis.
     */
    private static SqlQueryInfo createEnhancedSqlInfo(PreparedStatement statement, long startTime, 
                                                    long durationMs, SQLException thrown, Object result) {
        ConfigLoader config = ConfigLoader.getInstance();
        SqlQueryInfo info = new SqlQueryInfo();
        
        // Informações básicas
        info.timestamp = Instant.now().toString();
        info.durationMs = durationMs;
        info.threadName = Thread.currentThread().getName();
        info.slow = durationMs > config.getSqlSlowThresholdMs();
        
        // Captura e normalização da query
        extractAndNormalizeQuery(info, statement);
        
        // Informações de conexão e pool
        extractConnectionInfo(info, statement);
        
        // Informações de contexto da aplicação
        extractApplicationContext(info);
        
        // Métricas de resultado
        extractResultMetrics(info, result, thrown);
        
        // Performance insights
        if (info.normalizedQuery != null) {
            info.performanceInsight = SqlQueryNormalizer.extractPerformanceInsight(info.query);
        }
        
        // Tags customizadas
        addCustomTags(info, config);

        // Correlation com span atual
        try {
            if (config.isTracingEnabled()) {
                TraceSpan current = TracingManager.get().currentSpan();
                if (current != null) {
                    info.traceId = current.traceId;
                    info.parentSpanId = current.spanId;
                }
            }
        } catch (Throwable t) {
            // ignora
        }
        
        // Tratamento de erro
        if (thrown != null) {
            info.error = thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
        }
        
        return info;
    }
    
    private static void extractAndNormalizeQuery(SqlQueryInfo info, PreparedStatement statement) {
        try {
            // Primeiro tenta registry com SQL original
            String original = PreparedStatementRegistry.lookup(statement);
            if (original != null) {
                info.query = original;
            } else {
                info.query = statement.toString();
            }
            
            // Limita tamanho da query para evitar logs enormes
            if (info.query != null && info.query.length() > 2000) {
                info.query = info.query.substring(0, 2000) + "... [truncated]";
            }
            
            // Normalização e hash
            if (info.query != null) {
                info.normalizedQuery = SqlQueryNormalizer.normalize(info.query);
                info.queryHash = SqlQueryNormalizer.generateHash(info.normalizedQuery);
                info.queryType = extractQueryType(info.query);
            }
        } catch (Exception e) {
            info.query = "[Unable to extract query: " + e.getMessage() + "]";
            info.queryType = "UNKNOWN";
        }
    }
    
    private static void extractConnectionInfo(SqlQueryInfo info, PreparedStatement statement) {
        try {
            Connection conn = statement.getConnection();
            if (conn != null) {
                info.connectionUrl = conn.getMetaData().getURL();
                info.databaseName = conn.getCatalog();
                
                // Tenta extrair informações do pool (se disponível)
                extractPoolInfo(info, conn);
            }
        } catch (Exception e) {
            // Ignora erros de extração de informações de conexão
        }
    }
    
    private static void extractPoolInfo(SqlQueryInfo info, Connection conn) {
        try {
            // Tenta identificar connection pools comuns
            String connClassName = conn.getClass().getName();
            
            if (connClassName.contains("HikariCP")) {
                info.connectionPoolName = "HikariCP";
            } else if (connClassName.contains("C3P0")) {
                info.connectionPoolName = "C3P0";
            } else if (connClassName.contains("DBCP")) {
                info.connectionPoolName = "Apache DBCP";
            } else if (connClassName.contains("Tomcat")) {
                info.connectionPoolName = "Tomcat JDBC Pool";
            } else {
                info.connectionPoolName = "Unknown";
            }
        } catch (Exception e) {
            // Ignora erros
        }
    }
    
    private static void extractApplicationContext(SqlQueryInfo info) {
        try {
            // Obtém stack trace para identificar método chamador
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            
            // Procura pelo primeiro elemento que não seja do framework ou agent
            for (StackTraceElement element : stackTrace) {
                String className = element.getClassName();
                if (!className.startsWith("agent.") && 
                    !className.startsWith("net.bytebuddy.") && 
                    !className.startsWith("java.sql.") && 
                    !className.startsWith("javax.sql.") &&
                    !className.contains("$ByteBuddy$")) {
                    
                    info.className = className;
                    info.methodName = element.getMethodName();
                    info.lineNumber = element.getLineNumber();
                    break;
                }
            }
        } catch (Exception e) {
            // Ignora erros de extração de contexto
        }
    }
    
    private static void extractResultMetrics(SqlQueryInfo info, Object result, SQLException thrown) {
        if (thrown == null && result instanceof ResultSet) {
            try {
                ResultSet rs = (ResultSet) result;
                // Informações sobre fetch size (se disponível)
                info.fetchSize = rs.getFetchSize();
            } catch (Exception e) {
                // Ignora erros
            }
        }
        
        // Para UPDATE/INSERT/DELETE, tentaremos capturar rows affected em outro local
        // pois não temos acesso direto aqui
    }
    
    private static void addCustomTags(SqlQueryInfo info, ConfigLoader config) {
        // Adiciona tags baseadas em configuração
        info.tags.put("application", config.getApplicationName());
        info.tags.put("agent", config.getAgentName());
        
        // Tag baseada em complexidade
        if (info.query != null && SqlQueryNormalizer.isComplexQuery(info.query)) {
            info.tags.put("complexity", "high");
        } else {
            info.tags.put("complexity", "low");
        }
        
        // Tag baseada em performance
        if (info.slow) {
            info.tags.put("performance", "slow");
        } else if (info.durationMs < 10) {
            info.tags.put("performance", "fast");
        } else {
            info.tags.put("performance", "normal");
        }
    }

    public static String extractQueryType(String sql) {
        if (sql == null) return "UNKNOWN";
        Matcher matcher = QUERY_TYPE_PATTERN.matcher(sql);
        return matcher.find() ? matcher.group(1).toUpperCase() : "UNKNOWN";
    }
}