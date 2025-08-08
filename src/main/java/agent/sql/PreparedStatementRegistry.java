package agent.sql;

import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mantém mapeamento fraco entre PreparedStatement e a SQL original.
 */
public class PreparedStatementRegistry {
    private static final Map<PreparedStatement, String> map = Collections.synchronizedMap(new WeakHashMap<>());

    public static void register(PreparedStatement ps, String sql) {
        if (ps != null && sql != null) {
            map.put(ps, sql);
        }
    }

    public static String lookup(PreparedStatement ps) {
        if (ps == null) return null;
        return map.get(ps);
    }
}
