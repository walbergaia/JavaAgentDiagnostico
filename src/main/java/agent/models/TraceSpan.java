package agent.models;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Representa um span de tracing simples (inspirado em modelos de OpenTelemetry/Zipkin).
 * Mantido minimalista para baixo overhead.
 */
public class TraceSpan {
    public String traceId;
    public String spanId;
    public String parentSpanId;
    public String name;
    public String kind; // SERVER, CLIENT, INTERNAL
    public long startNano;
    public long endNano;
    public String status; // OK / ERROR
    public String errorMessage;
    public Map<String, String> attributes = new ConcurrentHashMap<>();

    public long durationMillis() {
        if (endNano <= startNano) return 0L;
        return (endNano - startNano) / 1_000_000L;
    }
}
