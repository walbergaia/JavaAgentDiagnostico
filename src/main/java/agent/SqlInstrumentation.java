package agent;

import agent.instrumentation.SqlTimingAdvice;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;


/**
 * Configura e aplica a instrumentação de SQL usando Byte Buddy.
 */
public class SqlInstrumentation {

    public static void setup(Instrumentation inst, ConfigLoader config) {
        if (!config.isSqlCaptureEnabled()) {
            System.out.println("Monitoramento de SQL está desabilitado.");
            return;
        }

        System.out.println("Aplicando instrumentação em java.sql.PreparedStatement...");

        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
            .type(ElementMatchers.isSubTypeOf(java.sql.PreparedStatement.class))
            .transform(new AgentBuilder.Transformer() {
                @Override
                public net.bytebuddy.dynamic.DynamicType.Builder<?> transform(
                    net.bytebuddy.dynamic.DynamicType.Builder<?> builder,
                    net.bytebuddy.description.type.TypeDescription typeDescription,
                    ClassLoader classLoader,
                    net.bytebuddy.utility.JavaModule javaModule,
                    java.security.ProtectionDomain protectionDomain) {
                    
                    return builder
                        .method(ElementMatchers.named("execute")
                            .or(ElementMatchers.named("executeQuery"))
                            .or(ElementMatchers.named("executeUpdate"))
                        )
                        .intercept(Advice.to(SqlTimingAdvice.class));
                }
            }).installOn(inst);
        
        System.out.println("Instrumentação de SQL aplicada com sucesso.");
    }
}