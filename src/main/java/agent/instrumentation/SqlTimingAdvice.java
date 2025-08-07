package agent.instrumentation;

import agent.ConfigLoader;
import agent.SqlRegistry;
import agent.models.SqlQueryInfo;
import net.bytebuddy.asm.Advice;

import java.sql.PreparedStatement;
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
     * Executado na saída do método instrumentado.
     * @param startTime O valor retornado pelo método onEnter.
     * @param statement O objeto PreparedStatement (`this` no método original).
     * @param thrown A exceção lançada pelo método, se houver.
     */
    @Advice.OnMethodExit(onThrowable = SQLException.class)
    public static void onExit(@Advice.Enter long startTime,
                              @Advice.This PreparedStatement statement,
                              @Advice.Thrown SQLException thrown) {

        ConfigLoader config = ConfigLoader.getInstance();
        long durationNs = System.nanoTime() - startTime;
        long durationMs = durationNs / 1_000_000;

        // Se a query for mais rápida que o threshold e não deu erro, podemos ignorar.
        if (thrown == null && durationMs < 1) { // Ignora queries sub-ms sem erro
            return;
        }

        SqlQueryInfo info = new SqlQueryInfo();
        info.timestamp = Instant.now().toString();
        info.durationMs = durationMs;
        info.threadName = Thread.currentThread().getName();
        info.query = statement.toString(); // Varia entre drivers, mas geralmente funciona.
        info.queryType = extractQueryType(info.query);
        info.slow = durationMs > config.getSqlSlowThresholdMs();

        if (thrown != null) {
            info.error = thrown.getMessage();
        }

        SqlRegistry.getInstance().add(info);
    }

    private static String extractQueryType(String sql) {
        if (sql == null) return "UNKNOWN";
        Matcher matcher = QUERY_TYPE_PATTERN.matcher(sql);
        return matcher.find() ? matcher.group(1).toUpperCase() : "UNKNOWN";
    }
}