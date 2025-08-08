package agent.instrumentation;

import agent.ConfigLoader;
import agent.tracing.TracingManager;
import agent.models.TraceSpan;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Instrumenta HttpURLConnection connect e getInputStream para criar spans CLIENT.
 */
public class HttpClientInstrumentation {

    public static void setup(Instrumentation inst, ConfigLoader config) {
        if (!config.isTracingEnabled()) return;
        try {
            new AgentBuilder.Default()
                .type(ElementMatchers.isSubTypeOf(HttpURLConnection.class))
                .transform((builder, td, cl, m, pd) -> builder
                    .method(ElementMatchers.named("connect"))
                    .intercept(Advice.to(ConnectAdvice.class))
                ).installOn(inst);

            // Intercepta getInputStream para garantir fim do span caso connect não tenha sido chamado explicitamente
            new AgentBuilder.Default()
                .type(ElementMatchers.isSubTypeOf(HttpURLConnection.class))
                .transform((builder, td, cl, m, pd) -> builder
                    .method(ElementMatchers.named("getInputStream"))
                    .intercept(Advice.to(GetInputStreamAdvice.class))
                ).installOn(inst);
            System.out.println("Instrumentação HttpURLConnection aplicada.");
        } catch (Throwable t) {
            System.err.println("Falha instrumentação HttpURLConnection: " + t.getMessage());
        }
    }

    private static class SpanHolder {
        static final ThreadLocal<TraceSpan> CURRENT = new ThreadLocal<>();
    }

    public static class ConnectAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This HttpURLConnection conn) {
            TracingManager tm = TracingManager.get();
            if (!tm.isEnabled()) return;
            if (SpanHolder.CURRENT.get() != null) return; // já iniciado
            try {
                URL url = conn.getURL();
                TraceSpan span = tm.startSpan("HTTP " + conn.getRequestMethod() + " " + url.getHost(), "CLIENT");
                if (span != null) {
                    span.attributes.put("http.url", url.toString());
                    span.attributes.put("http.method", conn.getRequestMethod());
                }
                SpanHolder.CURRENT.set(span);
            } catch (Exception ignored) { }
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void onExit(@Advice.This HttpURLConnection conn, @Advice.Thrown Throwable error) {
            TraceSpan span = SpanHolder.CURRENT.get();
            if (span != null) {
                try { span.attributes.put("http.status", String.valueOf(conn.getResponseCode())); } catch (Exception ignored) {}
                TracingManager.get().endSpan(span, error);
                SpanHolder.CURRENT.remove();
            }
        }
    }

    public static class GetInputStreamAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This HttpURLConnection conn) {
            // Se span não criado em connect(), cria aqui
            if (SpanHolder.CURRENT.get() == null) {
                TracingManager tm = TracingManager.get();
                if (!tm.isEnabled()) return;
                try {
                    URL url = conn.getURL();
                    TraceSpan span = tm.startSpan("HTTP " + conn.getRequestMethod() + " " + url.getHost(), "CLIENT");
                    if (span != null) {
                        span.attributes.put("http.url", url.toString());
                        span.attributes.put("http.method", conn.getRequestMethod());
                    }
                    SpanHolder.CURRENT.set(span);
                } catch (Exception ignored) { }
            }
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void onExit(@Advice.This HttpURLConnection conn, @Advice.Thrown Throwable error) {
            TraceSpan span = SpanHolder.CURRENT.get();
            if (span != null) {
                try { span.attributes.put("http.status", String.valueOf(conn.getResponseCode())); } catch (Exception ignored) {}
                TracingManager.get().endSpan(span, error);
                SpanHolder.CURRENT.remove();
            }
        }
    }
}
