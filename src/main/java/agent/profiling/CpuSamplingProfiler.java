package agent.profiling;

import agent.ConfigLoader;
import agent.models.CpuProfileSnapshot;
import agent.models.HotspotEntry;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Profiler de CPU via sampling de stack traces.
 * Estratégia simples: coleta a cada intervalo, agrega top frame e cadeia completa.
 */
public class CpuSamplingProfiler {
    private final ConfigLoader config;
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread samplerThread;

    // Aggregations
    private final ConcurrentHashMap<String, FrameStat> frameStats = new ConcurrentHashMap<>();
    private volatile int totalSamples = 0;
    private volatile int discarded = 0;

    public CpuSamplingProfiler(ConfigLoader config) {
        this.config = config;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        samplerThread = new Thread(this::loop, "JavaAgent-CPU-Sampler");
        samplerThread.setDaemon(true);
        samplerThread.start();
    }

    public void stop() {
        running.set(false);
        if (samplerThread != null) samplerThread.interrupt();
    }

    private void loop() {
        int interval = config.getCpuProfilingIntervalMs();
        boolean includeNonRunnable = config.isCpuProfilingIncludeNonRunnable();
        int maxFrames = config.getCpuProfilingMaxFrames();
        while (running.get()) {
            try {
                long[] ids = threadMXBean.getAllThreadIds();
                ThreadInfo[] infos = threadMXBean.getThreadInfo(ids, maxFrames);
                for (ThreadInfo info : infos) {
                    if (info == null) continue;
                    Thread.State st = info.getThreadState();
                    if (!includeNonRunnable && st != Thread.State.RUNNABLE) continue;
                    StackTraceElement[] stack = info.getStackTrace();
                    if (stack == null || stack.length == 0) continue;
                    totalSamples++;
                    // top frame
                    record(stack[0], true);
                    // full frames (exclusive not incremented)
                    for (int i = 1; i < stack.length; i++) {
                        record(stack[i], false);
                    }
                }
                Thread.sleep(interval);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                // Evita queda do sampler
            }
        }
    }

    private void record(StackTraceElement el, boolean self) {
        if (el == null) return;
        String symbol = el.getClassName() + "." + el.getMethodName() + ":" + el.getLineNumber();
        FrameStat fs = frameStats.computeIfAbsent(symbol, k -> new FrameStat());
        fs.samples.incrementAndGet();
        if (self) fs.self.incrementAndGet();
    }

    public CpuProfileSnapshot buildAndResetSnapshot() {
        if (totalSamples == 0) return null;
        CpuProfileSnapshot snap = new CpuProfileSnapshot();
        snap.timestamp = Instant.now().toString();
        snap.totalSamples = totalSamples;
        snap.discardedSamples = discarded;
        snap.uniqueFrames = frameStats.size();
        // calcula top N
        int topN = config.getCpuProfilingTopN();
        List<Map.Entry<String, FrameStat>> list = new ArrayList<>(frameStats.entrySet());
        list.sort((a,b) -> Integer.compare(b.getValue().samples.get(), a.getValue().samples.get()));
        int count = 0;
        for (Map.Entry<String, FrameStat> e : list) {
            if (count++ >= topN) break;
            HotspotEntry h = new HotspotEntry();
            h.symbol = e.getKey();
            h.samples = e.getValue().samples.get();
            h.selfSamples = e.getValue().self.get();
            h.pct = (snap.totalSamples > 0) ? (h.samples * 100.0 / snap.totalSamples) : 0.0;
            snap.top.add(h);
        }
        // reset para próximo intervalo
        frameStats.clear();
        totalSamples = 0;
        discarded = 0;
        return snap;
    }

    private static class FrameStat {
        final java.util.concurrent.atomic.AtomicInteger samples = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger self = new java.util.concurrent.atomic.AtomicInteger();
    }
}
