package agent;

import agent.models.SqlQueryInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Um registro central e thread-safe para armazenar informações de queries SQL
 * coletadas pela instrumentação.
 */
public class SqlRegistry {

    private static final SqlRegistry INSTANCE = new SqlRegistry();
    private final List<SqlQueryInfo> capturedQueries = new CopyOnWriteArrayList<>();

    private SqlRegistry() {}

    public static SqlRegistry getInstance() {
        return INSTANCE;
    }

    public void add(SqlQueryInfo info) {
        capturedQueries.add(info);
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
}