package agent.io;

import agent.models.IoSocketStat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

public class IoSocketTracker {
    private static final IoSocketTracker INSTANCE = new IoSocketTracker();
    private final ConcurrentHashMap<String, Stat> stats = new ConcurrentHashMap<>();

    public static IoSocketTracker get() { return INSTANCE; }

    public void recordConnect(String remote) {
        if (remote == null) return;
        stats.computeIfAbsent(remote, k -> new Stat()).connectCount.incrementAndGet();
    }

    public void recordRead(String remote, int bytes) {
        if (remote == null || bytes <= 0) return;
        Stat s = stats.computeIfAbsent(remote, k -> new Stat());
        s.readBytes.addAndGet(bytes);
    }

    public void recordWrite(String remote, int bytes) {
        if (remote == null || bytes <= 0) return;
        Stat s = stats.computeIfAbsent(remote, k -> new Stat());
        s.writeBytes.addAndGet(bytes);
    }

    public List<IoSocketStat> top(int limit, long[] totalsOut) {
        List<IoSocketStat> out = new ArrayList<>();
        long totalR=0,totalW=0;
        for (Stat s : stats.values()) { totalR+=s.readBytes.get(); totalW+=s.writeBytes.get(); }
        List<Map.Entry<String, Stat>> entries = new ArrayList<>(stats.entrySet());
        entries.sort((a,b)-> Long.compare((b.getValue().readBytes.get()+b.getValue().writeBytes.get()), (a.getValue().readBytes.get()+a.getValue().writeBytes.get())));
        int c=0;
        for (Map.Entry<String, Stat> e : entries) {
            if (c++>=limit) break;
            IoSocketStat st = new IoSocketStat();
            st.remote = e.getKey();
            st.readBytes = e.getValue().readBytes.get();
            st.writeBytes = e.getValue().writeBytes.get();
            st.connectCount = e.getValue().connectCount.get();
            out.add(st);
        }
        totalsOut[0] = totalR;
        totalsOut[1] = totalW;
        return out;
    }

    private static class Stat {
        java.util.concurrent.atomic.AtomicLong readBytes = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLong writeBytes = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicInteger connectCount = new java.util.concurrent.atomic.AtomicInteger();
    }
}
