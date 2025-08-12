package agent.sql;

import agent.models.SqlQueryInfo;
import agent.ConfigLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * Mantém estatísticas agregadas de queries por hash normalizado.
 */
public class SqlAggregationRegistry {
    private static final SqlAggregationRegistry INSTANCE = new SqlAggregationRegistry();
    private final ConcurrentHashMap<String, Entry> stats = new ConcurrentHashMap<>();

    public static SqlAggregationRegistry get() { return INSTANCE; }

    public void record(SqlQueryInfo info, ConfigLoader config) {
        if (info == null || info.queryHash == null) return;
        Entry e = stats.computeIfAbsent(info.queryHash, k -> new Entry(info.normalizedQuery, info.queryType));
        e.count.incrementAndGet();
        e.totalDuration.addAndGet(info.durationMs);
        updateMax(e, info.durationMs);
        updateMin(e, info.durationMs);
        if (info.slow) e.slowCount.incrementAndGet();
    }

    private void updateMax(Entry e, long dur) {
        long prev;
        do {
            prev = e.maxDuration.get();
            if (dur <= prev) return;
        } while (!e.maxDuration.compareAndSet(prev, dur));
    }

    private void updateMin(Entry e, long dur) {
        long prev;
        do {
            prev = e.minDuration.get();
            if (prev != -1 && dur >= prev) return;
        } while (!e.minDuration.compareAndSet(prev, dur));
    }

    public List<SqlQueryInfo> drainAggregated(ConfigLoader config) {
        if (!config.isSqlAggregationEnabled()) return Collections.emptyList();
        int limit = config.getSqlAggregationMaxEntries();
        List<SqlQueryInfo> out = new ArrayList<>();
        Iterator<Map.Entry<String, Entry>> it = stats.entrySet().iterator();
        while (it.hasNext() && out.size() < limit) {
            Map.Entry<String, Entry> mapEntry = it.next();
            Entry e = mapEntry.getValue();
            SqlQueryInfo agg = new SqlQueryInfo();
            agg.queryHash = mapEntry.getKey();
            agg.normalizedQuery = e.normalized;
            agg.queryType = e.type;
            int c = e.count.get();
            agg.executionCount = c;
            agg.maxDurationMs = e.maxDuration.get();
            long min = e.minDuration.get();
            if (min == -1) min = 0; // quando nenhum valor definido
            agg.minDurationMs = min;
            long total = e.totalDuration.get();
            agg.totalTimeMs = total;
            if (c > 0) agg.avgDurationMs = total / c;
            agg.tags.put("aggregated", "true");
            agg.tags.put("slow.count", String.valueOf(e.slowCount.get()));
            out.add(agg);
            it.remove();
        }
        return out;
    }

    private static class Entry {
        final String normalized;
        final String type;
        final AtomicInteger count = new AtomicInteger();
        final AtomicInteger slowCount = new AtomicInteger();
        final AtomicLong totalDuration = new AtomicLong();
        final AtomicLong maxDuration = new AtomicLong(-1);
        final AtomicLong minDuration = new AtomicLong(-1);
        Entry(String n, String t) { this.normalized = n; this.type = t; }
    }
}
