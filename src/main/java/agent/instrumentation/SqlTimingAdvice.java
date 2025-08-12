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
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O "Advice" do Byte Buddy que contém a lógica a ser injetada nos métodos de PreparedStatement.
 * Captura informações detalhadas sobre execução de prepared statements.
 */
public class SqlTimingAdvice {

    // Pattern simples para extrair o tipo de query.
    private static final Pattern QUERY_TYPE_PATTERN = Pattern.compile("^\\s*(select|insert|update|delete|call|with|merge|truncate)", Pattern.CASE_INSENSITIVE);
    
    // Thread local para armazenar informações da thread
    private static final ThreadLocal<Map<String, Object>> threadLocalInfo = new ThreadLocal<>();
    
    // Cache para tracking de conexões
    private static final Map<Integer, Long> connectionAcquireTimes = new ConcurrentHashMap<>();

    /**
     * Executado na entrada do método instrumentado.
     * Captura o tempo de início e informações da thread.
     * @return O tempo de início em nanossegundos.
     */
    @Advice.OnMethodEnter
    public static long onEnter(@Advice.This PreparedStatement statement) {
        long startNanos = System.nanoTime();
        
        // Captura informações da thread no momento da entrada
        Thread currentThread = Thread.currentThread();
        
        // Armazena estado da thread para análise posterior
        if (statement != null) {
            try {
                Map<String, Object> info = new HashMap<>();
                info.put("threadState", currentThread.getState());
                info.put("threadId", currentThread.getId());
                info.put("threadName", currentThread.getName());
                info.put("startTimeMillis", System.currentTimeMillis());
                info.put("stackTrace", captureStackTrace(currentThread));
                threadLocalInfo.set(info);
                
                // Registra tempo de aquisição da conexão se disponível
                Connection conn = statement.getConnection();
                if (conn != null) {
                    connectionAcquireTimes.putIfAbsent(conn.hashCode(), System.currentTimeMillis());
                }
            } catch (Exception ignored) {
                // Ignora erros para não afetar a aplicação
            }
        }
        
        return startNanos;
    }

    /**
     * Executado na saída do método instrumentado - versão aprimorada.
     */
    @Advice.OnMethodExit(onThrowable = SQLException.class)
    public static void onExit(@Advice.Enter long startTime,
                              @Advice.This PreparedStatement statement,
                              @Advice.Thrown SQLException thrown,
                              @Advice.Return Object result,
                              @Advice.Origin("#m") String methodName) {

        try {
            long endNanos = System.nanoTime();
            long durationNs = endNanos - startTime;
            long durationMicros = durationNs / 1_000; // Converte para microssegundos
            long durationMs = durationNs / 1_000_000;

            // Filtra queries muito rápidas apenas se não houve erro
            if (thrown == null && durationMicros < 100) { // Menos de 100 microssegundos
                return;
            }

            SqlQueryInfo info = createEnhancedSqlInfo(statement, startTime, durationMicros, durationMs, thrown, result, methodName);
            
            // Captura rows affected para UPDATE/INSERT/DELETE
            if (result instanceof Integer) {
                info.rowsAffected = (Integer) result;
                // Atualiza estatísticas de agregação
                if (info.queryType != null && 
                    (info.queryType.equals("INSERT") || info.queryType.equals("UPDATE") || 
                     info.queryType.equals("DELETE") || info.queryType.equals("MERGE"))) {
                    info.totalTimeMs = durationMs;
                }
            }
            
            // Captura informações de ResultSet para SELECT
            if (result instanceof ResultSet) {
                try {
                    ResultSet rs = (ResultSet) result;
                    info.fetchSize = rs.getFetchSize();
                    info.queryType = "SELECT";
                    // Tenta contar linhas (cuidado com performance)
                    if (rs.isBeforeFirst()) {
                        int rowCount = 0;
                        while (rs.next() && rowCount < 1000) { // Limita contagem
                            rowCount++;
                        }
                        info.rowsFetched = rowCount;
                        rs.beforeFirst(); // Reseta cursor
                    }
                } catch (Exception ignored) {}
            }
            
            // Log para queries bloqueadas
            if (info.threadBlocked) {
                System.out.println("[SQL BLOCKED] Thread: " + info.threadName + 
                    " | Duration: " + durationMs + "ms (" + durationMicros + "μs)" +
                    " | Query: " + truncateQuery(info.normalizedQuery, 100));
            }
            
            // Log slow queries
            if (info.slow) {
                System.out.println("[SQL SLOW] Type: " + info.eventType + 
                    " | Query Type: " + info.queryType +
                    " | Duration: " + durationMs + "ms (" + durationMicros + "μs)" +
                    " | Connection: " + info.connectionId +
                    " | Thread: " + info.threadName +
                    " | Query: " + truncateQuery(info.normalizedQuery, 100));
            }

            // Adiciona ao registry para agregação
            SqlRegistry.getInstance().add(info);
            
        } catch (Throwable t) {
            // Silencia erros da instrumentação para não quebrar a aplicação principal
            System.err.println("[SQL Agent] Warning: Instrumentation error (app not affected): " + t.getMessage());
        } finally {
            // Limpa thread local
            threadLocalInfo.remove();
        }
    }
    
    /**
     * Cria um objeto SqlQueryInfo aprimorado com todas as métricas disponíveis.
     */
    private static SqlQueryInfo createEnhancedSqlInfo(PreparedStatement statement, long startTimeNanos, 
                                                    long durationMicros, long durationMs, 
                                                    SQLException thrown, Object result, String methodName) {
        ConfigLoader config = ConfigLoader.getInstance();
        SqlQueryInfo info = new SqlQueryInfo();
        
        // === Informações de Execução ===
        
        // Horário de início - recupera do thread local ou calcula
        Map<String, Object> threadInfo = threadLocalInfo.get();
        
        if (threadInfo != null && threadInfo.containsKey("startTimeMillis")) {
            info.startTimeMillis = (Long) threadInfo.get("startTimeMillis");
        } else {
            // Calcula baseado no tempo atual menos a duração
            info.startTimeMillis = System.currentTimeMillis() - durationMs;
        }
        info.startTime = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(info.startTimeMillis));
        
        // Tipo de evento baseado no método executado
        if (methodName.contains("executeBatch")) {
            info.eventType = "BATCH_EXECUTION";
        } else if (methodName.contains("executeUpdate")) {
            info.eventType = "STATEMENT_EXECUTION";
        } else {
            info.eventType = "PREPARED_STATEMENT_EXECUTION";
        }
        info.isPreparedStatement = true;
        
        // Duração em microssegundos e milissegundos
        info.durationMicros = durationMicros;
        info.durationMs = durationMs;
        
        // Connection ID
        extractConnectionId(info, statement);
        
        // Thread information
        Thread currentThread = Thread.currentThread();
        info.threadName = currentThread.getName();
        info.threadId = currentThread.getId();
        info.updateThreadState(currentThread.getState());
        
        // Verifica se thread estava bloqueada no início
        if (threadInfo != null) {
            if (threadInfo.containsKey("threadState")) {
                Thread.State initialState = (Thread.State) threadInfo.get("threadState");
                if (initialState == Thread.State.BLOCKED) {
                    info.threadBlocked = true;
                    info.tags.put("thread_blocked_at_start", "true");
                }
            }
            
            // Stack trace resumido
            if (threadInfo.containsKey("stackTrace")) {
                info.stackTrace = (String) threadInfo.get("stackTrace");
            }
        }
        
        // Marca como slow query
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
            analyzeQueryPerformance(info);
        }
        
        // Inicializa métricas de agregação
        info.executionCount = 1;
        info.totalTimeMs = durationMs;
        info.avgDurationMs = durationMs;
        info.maxDurationMs = durationMs;
        info.minDurationMs = durationMs;
        
        // Tags customizadas
        addCustomTags(info, config);

        // Correlation com span atual (distributed tracing)
        try {
            if (config.isTracingEnabled()) {
                TraceSpan current = TracingManager.get().currentSpan();
                if (current != null) {
                    info.traceId = current.traceId;
                    info.spanId = java.util.UUID.randomUUID().toString();
                    info.parentSpanId = current.spanId;
                }
            }
        } catch (Throwable t) {
            // ignora
        }
        
        // Tratamento de erro
        if (thrown != null) {
            info.error = thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
            info.tags.put("error", "true");
            info.tags.put("error_type", thrown.getClass().getSimpleName());
        }
        
        return info;
    }
    
    private static void extractConnectionId(SqlQueryInfo info, PreparedStatement statement) {
        try {
            // Connection ID baseado no hashCode da conexão
            Connection conn = statement.getConnection();
            if (conn != null) {
                info.connectionHashCode = conn.hashCode();
                info.connectionId = "conn-" + Integer.toHexString(conn.hashCode());
                
                // Calcula tempo de aquisição da conexão se disponível
                Long acquireTime = connectionAcquireTimes.get(conn.hashCode());
                if (acquireTime != null) {
                    info.connectionAcquireTimeMs = info.startTimeMillis - acquireTime;
                }
            }
            
            // PreparedStatement ID
            info.preparedStatementId = "pstmt-" + Integer.toHexString(statement.hashCode());
        } catch (Exception e) {
            // Ignora erros
        }
    }
    
    private static void extractAndNormalizeQuery(SqlQueryInfo info, PreparedStatement statement) {
        try {
            // Primeiro tenta registry com SQL original
            String original = PreparedStatementRegistry.lookup(statement);
            if (original != null) {
                info.query = original;
                info.queryWithParams = statement.toString(); // toString pode ter os valores reais
            } else {
                info.query = statement.toString();
                info.queryWithParams = info.query;
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
                
                // Tenta extrair parâmetros se possível
                extractParameters(info, statement);
            }
        } catch (Exception e) {
            info.query = "[Unable to extract query: " + e.getMessage() + "]";
            info.queryType = "UNKNOWN";
        }
    }
    
    private static void extractParameters(SqlQueryInfo info, PreparedStatement statement) {
        try {
            // Tenta extrair parâmetros comparando query original com toString()
            String stmtString = statement.toString();
            if (stmtString != null && info.query != null && !stmtString.equals(info.query)) {
                // Se toString() tem valores diferentes, provavelmente contém os parâmetros
                info.queryWithParams = stmtString;
                
                // Tenta identificar valores de parâmetros (heurística simples)
                if (stmtString.contains("=") && !info.query.contains("=")) {
                    info.tags.put("has_parameters", "true");
                }
            }
        } catch (Exception ignored) {
            // Ignora erros na extração de parâmetros
        }
    }
    
    private static void extractConnectionInfo(SqlQueryInfo info, PreparedStatement statement) {
        try {
            Connection conn = statement.getConnection();
            if (conn != null) {
                // Connection ID já foi extraído
                
                // URL e database
                info.connectionUrl = conn.getMetaData().getURL();
                info.databaseName = conn.getCatalog();
                
                // Database product info
                String dbProduct = conn.getMetaData().getDatabaseProductName();
                String dbVersion = conn.getMetaData().getDatabaseProductVersion();
                info.tags.put("database_product", dbProduct);
                info.tags.put("database_version", dbVersion);
                
                // Tenta extrair informações do pool
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
            
            if (connClassName.contains("HikariCP") || connClassName.contains("hikari")) {
                info.connectionPoolName = "HikariCP";
            } else if (connClassName.contains("C3P0") || connClassName.contains("c3p0")) {
                info.connectionPoolName = "C3P0";
            } else if (connClassName.contains("DBCP") || connClassName.contains("dbcp")) {
                info.connectionPoolName = "Apache DBCP";
            } else if (connClassName.contains("Tomcat")) {
                info.connectionPoolName = "Tomcat JDBC Pool";
            } else if (connClassName.contains("Druid")) {
                info.connectionPoolName = "Alibaba Druid";
            } else if (connClassName.contains("Vibur")) {
                info.connectionPoolName = "Vibur DBCP";
            } else {
                info.connectionPoolName = connClassName.substring(connClassName.lastIndexOf('.') + 1);
            }
            
            info.tags.put("connection_class", connClassName);
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
                    !className.startsWith("com.sun.") &&
                    !className.startsWith("sun.") &&
                    !className.contains("$ByteBuddy$") &&
                    !className.contains("$$")) {
                    
                    info.className = className;
                    info.methodName = element.getMethodName();
                    info.lineNumber = element.getLineNumber();
                    
                    // Adiciona informação do pacote
                    int lastDot = className.lastIndexOf('.');
                    if (lastDot > 0) {
                        info.tags.put("package", className.substring(0, lastDot));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // Ignora erros de extração de contexto
        }
    }
    
    private static String captureStackTrace(Thread thread) {
        try {
            StackTraceElement[] stackTrace = thread.getStackTrace();
            StringBuilder sb = new StringBuilder();
            int count = 0;
            
            for (StackTraceElement element : stackTrace) {
                String className = element.getClassName();
                // Captura apenas elementos relevantes da aplicação
                if (!className.startsWith("java.") && 
                    !className.startsWith("javax.") &&
                    !className.startsWith("sun.") &&
                    !className.startsWith("com.sun.") &&
                    !className.startsWith("agent.") &&
                    !className.startsWith("net.bytebuddy.")) {
                    
                    if (count > 0) sb.append(" <- ");
                    sb.append(element.getClassName()).append(".")
                      .append(element.getMethodName())
                      .append("(").append(element.getLineNumber()).append(")");
                    
                    if (++count >= 5) break; // Limita a 5 frames
                }
            }
            
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    private static void extractResultMetrics(SqlQueryInfo info, Object result, SQLException thrown) {
        if (thrown == null && result instanceof ResultSet) {
            try {
                ResultSet rs = (ResultSet) result;
                // Informações sobre fetch size
                info.fetchSize = rs.getFetchSize();
                
                // Tipo de ResultSet
                int type = rs.getType();
                if (type == ResultSet.TYPE_FORWARD_ONLY) {
                    info.tags.put("resultset_type", "FORWARD_ONLY");
                } else if (type == ResultSet.TYPE_SCROLL_INSENSITIVE) {
                    info.tags.put("resultset_type", "SCROLL_INSENSITIVE");
                } else if (type == ResultSet.TYPE_SCROLL_SENSITIVE) {
                    info.tags.put("resultset_type", "SCROLL_SENSITIVE");
                }
            } catch (Exception e) {
                // Ignora erros
            }
        }
    }
    
    private static void analyzeQueryPerformance(SqlQueryInfo info) {
        // Análise heurística de performance baseada na query
        String query = info.normalizedQuery.toLowerCase();
        
        // Detecta potenciais problemas
        if (query.contains("select *")) {
            if (info.performanceInsight == null) {
                info.performanceInsight = "SELECT_STAR";
            }
            info.tags.put("warning", "select_star_detected");
        }
        
        if (!query.contains("where") && query.startsWith("select")) {
            if (info.performanceInsight == null) {
                info.performanceInsight = "NO_WHERE_CLAUSE";
            }
            info.tags.put("warning", "no_where_clause");
        }
        
        if (query.contains("like '%")) {
            if (info.performanceInsight == null) {
                info.performanceInsight = "LEADING_WILDCARD";
            }
            info.tags.put("warning", "leading_wildcard");
        }
        
        // Detecta JOINs complexos
        int joinCount = countOccurrences(query, " join ");
        if (joinCount > 3) {
            info.tags.put("complexity", "very_high");
            info.tags.put("join_count", String.valueOf(joinCount));
        } else if (joinCount > 1) {
            info.tags.put("complexity", "high");
            info.tags.put("join_count", String.valueOf(joinCount));
        }
        
        // Detecta subqueries
        if (query.contains("(select")) {
            info.tags.put("has_subquery", "true");
        }
    }
    
    private static void addCustomTags(SqlQueryInfo info, ConfigLoader config) {
        // Adiciona tags baseadas em configuração
        info.tags.put("application", config.getApplicationName());
        info.tags.put("agent", config.getAgentName());
        info.tags.put("environment", System.getProperty("app.env", "unknown"));
        
        // Tag baseada em performance
        if (info.slow) {
            info.tags.put("performance", "slow");
        } else if (info.durationMs < 10) {
            info.tags.put("performance", "fast");
        } else {
            info.tags.put("performance", "normal");
        }
        
        // Tag baseada no tipo de operação
        if (info.queryType != null) {
            if (info.queryType.equals("SELECT")) {
                info.tags.put("operation", "read");
            } else if (info.queryType.equals("INSERT") || info.queryType.equals("UPDATE") || 
                       info.queryType.equals("DELETE") || info.queryType.equals("MERGE")) {
                info.tags.put("operation", "write");
            } else {
                info.tags.put("operation", "other");
            }
        }
    }
    
    private static String truncateQuery(String query, int maxLength) {
        if (query == null) return "[null]";
        if (query.length() <= maxLength) return query;
        return query.substring(0, maxLength) + "...";
    }
    
    private static int countOccurrences(String str, String substr) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(substr, idx)) != -1) {
            count++;
            idx += substr.length();
        }
        return count;
    }

    public static String extractQueryType(String sql) {
        if (sql == null) return "UNKNOWN";
        Matcher matcher = QUERY_TYPE_PATTERN.matcher(sql);
        return matcher.find() ? matcher.group(1).toUpperCase() : "UNKNOWN";
    }
}
