package agent.tracing;

import agent.ConfigLoader;
import agent.models.AgentMetrics;
import agent.models.TraceSpan;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerencia criação e ciclo de vida de spans simples.
 */
public class TracingManager {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final TracingManager INSTANCE = new TracingManager();
    private final List<TraceSpan> buffer = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Deque<TraceSpan>> context = ThreadLocal.withInitial(ArrayDeque::new);

    private volatile ConfigLoader config;

    public static TracingManager get() { return INSTANCE; }

    public void init(ConfigLoader cfg) { this.config = cfg; }

    public boolean isEnabled() { return config != null && config.isTracingEnabled(); }

    public TraceSpan startSpan(String name, String kind) {
        if (!isEnabled()) return null;
        if (!sample()) return null;
        if (buffer.size() >= config.getTracingMaxSpansPerInterval()) return null;

        long start = System.nanoTime();
        TraceSpan span = new TraceSpan();
        span.name = name;
        span.kind = kind;
        span.startNano = start;

        Deque<TraceSpan> stack = context.get();
        TraceSpan parent = stack.peek();
        if (parent != null) {
            span.parentSpanId = parent.spanId;
            span.traceId = parent.traceId;
        } else {
            span.traceId = generateId(32); // 128-bit hex
        }
        span.spanId = generateId(16); // 64-bit hex

        stack.push(span);
        return span;
    }

    public void endSpan(TraceSpan span, Throwable error) {
        if (span == null) return;
        span.endNano = System.nanoTime();
        if (error != null) {
            span.status = "ERROR";
            span.errorMessage = error.getClass().getSimpleName() + ":" + error.getMessage();
        } else {
            span.status = "OK";
        }
        buffer.add(span);
        Deque<TraceSpan> stack = context.get();
        if (!stack.isEmpty() && stack.peek() == span) {
            stack.pop();
        } else {
            stack.remove(span); // desalinhado
        }
    }

    /**
     * Retorna o span atual (topo da pilha) sem alterar estado.
     */
    public TraceSpan currentSpan() {
        Deque<TraceSpan> stack = context.get();
        return stack.peek();
    }

    public void injectCollected(AgentMetrics metrics) {
        if (!isEnabled()) return;
        if (buffer.isEmpty()) return;
        metrics.spans.addAll(buffer);
        buffer.clear();
    }

    private boolean sample() {
        double rate = config.getTracingSampleRate();
        if (rate >= 1.0) return true;
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    private String generateId(int hexLen) {
        byte[] b = new byte[hexLen / 2];
        RANDOM.nextBytes(b);
        char[] out = new char[hexLen];
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
