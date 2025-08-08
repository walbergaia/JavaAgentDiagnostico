package agent.instrumentation;

import agent.ConfigLoader;
import agent.tracing.TracingManager;
import agent.models.TraceSpan;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.lang.instrument.Instrumentation;

/**
 * Instrumenta javax.servlet.Servlet.service para criar spans SERVER.
 */
public class HttpServletInstrumentation {

    public static void setup(Instrumentation inst, ConfigLoader config) {
        if (!config.isTracingEnabled() || !config.isHttpTracingEnabled()) {
            return;
        }
        try {
            new AgentBuilder.Default()
                .type(ElementMatchers.isSubTypeOf(Servlet.class))
                .transform((builder, typeDescription, classLoader, module, pd) ->
                    builder.method(ElementMatchers.named("service"))
                        .intercept(Advice.to(ServiceAdvice.class))
                ).installOn(inst);
            System.out.println("Instrumentação HTTP Servlet aplicada.");
        } catch (Throwable t) {
            System.err.println("Falha instrumentação HTTP: " + t.getMessage());
        }
    }

    public static class ServiceAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static TraceSpan onEnter(@Advice.This Object thiz,
                                        @Advice.Argument(0) ServletRequest req) {
            TracingManager tm = TracingManager.get();
            if (!tm.isEnabled()) return null;
            String path = req != null ? req.getServletContext().getContextPath() : "/";
            TraceSpan span = tm.startSpan(path.isEmpty()?"HTTP /":"HTTP " + path, "SERVER");
            if (span != null) {
                span.attributes.put("component", "servlet");
                if (req != null) {
                    span.attributes.put("peer.ip", req.getRemoteAddr());
                }
            }
            return span;
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void onExit(@Advice.Enter TraceSpan span,
                                  @Advice.Argument(0) ServletRequest req,
                                  @Advice.Argument(1) ServletResponse resp,
                                  @Advice.Thrown Throwable error) {
            TracingManager tm = TracingManager.get();
            if (span != null) {
                if (resp != null) {
                    try {
                        span.attributes.put("http.status", String.valueOf(resp.getClass().getMethod("getStatus").invoke(resp)));
                    } catch (Exception ignored) { }
                }
                tm.endSpan(span, error);
            }
        }
    }
}
