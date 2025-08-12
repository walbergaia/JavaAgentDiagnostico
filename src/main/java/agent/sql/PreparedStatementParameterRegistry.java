package agent.sql;

import java.sql.PreparedStatement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.WeakHashMap;

/**
 * Mantém parâmetros atribuídos a PreparedStatements para reconstrução de SQL com valores.
 */
public class PreparedStatementParameterRegistry {
    private static final Map<PreparedStatement, ConcurrentHashMap<Integer, Object>> PARAMS =
        java.util.Collections.synchronizedMap(new WeakHashMap<>());

    public static void set(PreparedStatement ps, int index, Object value) {
        if (ps == null || index <= 0) return;
        ConcurrentHashMap<Integer,Object> map = PARAMS.get(ps);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            PARAMS.put(ps, map);
        }
        map.put(index, value);
    }

    public static Map<Integer,Object> get(PreparedStatement ps) {
        if (ps == null) return java.util.Collections.emptyMap();
        Map<Integer,Object> m = PARAMS.get(ps);
        if (m == null) return java.util.Collections.emptyMap();
        return m;
    }
}
