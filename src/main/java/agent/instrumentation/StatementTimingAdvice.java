package agent.instrumentation;

import agent.ConfigLoader;
import agent.SqlRegistry;
import agent.models.SqlQueryInfo;
import agent.sql.SqlQueryNormalizer;
import net.bytebuddy.asm.Advice;
import agent.tracing.TracingManager;
import agent.models.TraceSpan;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Advice para java.sql.Statement (não PreparedStatement). */
public class StatementTimingAdvice {
    private static final Pattern QUERY_TYPE_PATTERN = Pattern.compile("^\\s*(select|insert|update|delete|call|with)", Pattern.CASE_INSENSITIVE);

    @Advice.OnMethodEnter
    public static Object[] onEnter(@Advice.Argument(0) Object sqlArg) {
        long start = System.nanoTime();
        String sql = null;
        if (sqlArg instanceof String) sql = (String) sqlArg;
        return new Object[]{start, sql};
    }

    @Advice.OnMethodExit(onThrowable = SQLException.class)
    public static void onExit(@Advice.This Statement stmt,
                              @Advice.Enter Object[] ctx,
                              @Advice.Thrown SQLException thrown,
                              @Advice.Return Object result) {
        long start = (Long) ctx[0];
        String sql = (String) ctx[1];
        long durMs = (System.nanoTime() - start)/1_000_000;
        if (sql == null) return;
        if (thrown == null && durMs < 1) return; // ignora execuções ultra rápidas sem erro
        try {
            ConfigLoader config = ConfigLoader.getInstance();
            SqlQueryInfo info = new SqlQueryInfo();
            info.timestamp = Instant.now().toString();
            info.durationMs = durMs;
            info.threadName = Thread.currentThread().getName();
            info.query = sql;
            if (sql.length() > 2000) info.query = sql.substring(0,2000)+"... [truncated]";
            info.normalizedQuery = SqlQueryNormalizer.normalize(info.query);
            info.queryHash = SqlQueryNormalizer.generateHash(info.normalizedQuery);
            info.queryType = extractQueryType(sql);
            info.slow = durMs > config.getSqlSlowThresholdMs();
            if (result instanceof Integer) info.rowsAffected = (Integer) result;
            if (result instanceof ResultSet) {
                try { info.fetchSize = ((ResultSet) result).getFetchSize(); } catch (Exception ignored) {}
            }
            if (thrown != null) info.error = thrown.getClass().getSimpleName()+": "+thrown.getMessage();
            // tracing
            if (config.isTracingEnabled()) {
                try { TraceSpan current = TracingManager.get().currentSpan(); if (current != null) { info.traceId = current.traceId; info.parentSpanId = current.spanId; } } catch (Throwable ignored) {}
            }
            // contexto
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            for (StackTraceElement el : st) {
                String cn = el.getClassName();
                if (!cn.startsWith("agent.") && !cn.startsWith("net.bytebuddy") && !cn.startsWith("java.sql") && !cn.contains("$ByteBuddy$")) {
                    info.className = cn; info.methodName = el.getMethodName(); info.lineNumber = el.getLineNumber(); break;
                }
            }
            info.tags.put("application", config.getApplicationName());
            info.tags.put("agent", config.getAgentName());
            SqlRegistry.getInstance().add(info);
        } catch (Throwable t) {
            System.err.println("AVISO: Erro instrumentação Statement: "+t.getMessage());
        }
    }

    private static String extractQueryType(String sql) {
        Matcher m = QUERY_TYPE_PATTERN.matcher(sql); return m.find()? m.group(1).toUpperCase():"UNKNOWN";
    }
}
