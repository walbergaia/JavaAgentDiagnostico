package agent.io;

import agent.models.IoFileStat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

public class IoFileTracker {
    private static final IoFileTracker INSTANCE = new IoFileTracker();
    private final ConcurrentHashMap<String, Stat> stats = new ConcurrentHashMap<>();

    public static IoFileTracker get() { return INSTANCE; }

    public void recordRead(String path, int bytes) {
        if (path == null) return;
        Stat s = stats.computeIfAbsent(path, k -> new Stat());
        if (bytes > 0) {
            s.readBytes.addAndGet(bytes);
            s.readOps.incrementAndGet();
        }
    }

    public void recordWrite(String path, int bytes) {
        if (path == null) return;
        Stat s = stats.computeIfAbsent(path, k -> new Stat());
        if (bytes > 0) {
            s.writeBytes.addAndGet(bytes);
            s.writeOps.incrementAndGet();
        }
    }

    public List<IoFileStat> top(int limit, long[] totalsOut) {
        List<IoFileStat> list = new ArrayList<>();
        long totalR = 0, totalW = 0;
        for (Map.Entry<String, Stat> e : stats.entrySet()) {
            Stat st = e.getValue();
            totalR += st.readBytes.get();
            totalW += st.writeBytes.get();
        }
        List<Map.Entry<String, Stat>> entries = new ArrayList<>(stats.entrySet());
        entries.sort((a,b) -> Long.compare((b.getValue().readBytes.get()+b.getValue().writeBytes.get()), (a.getValue().readBytes.get()+a.getValue().writeBytes.get())));
        int c=0;
        for (Map.Entry<String, Stat> e : entries) {
            if (c++ >= limit) break;
            IoFileStat fs = new IoFileStat();
            fs.path = e.getKey();
            fs.readBytes = e.getValue().readBytes.get();
            fs.writeBytes = e.getValue().writeBytes.get();
            fs.readOps = e.getValue().readOps.get();
            fs.writeOps = e.getValue().writeOps.get();
            list.add(fs);
        }
        totalsOut[0] = totalR;
        totalsOut[1] = totalW;
        return list;
    }

    private static class Stat { 
        java.util.concurrent.atomic.AtomicLong readBytes = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLong writeBytes = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicInteger readOps = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger writeOps = new java.util.concurrent.atomic.AtomicInteger();
    }
}
