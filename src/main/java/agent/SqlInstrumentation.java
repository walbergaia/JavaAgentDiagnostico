package agent;

import agent.instrumentation.SqlTimingAdvice;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.asm.Advice;


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
            .with(AgentBuilder.InitializationStrategy.SelfInjection.EAGER)
            .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
            .type(ElementMatchers.isSubTypeOf(java.sql.PreparedStatement.class))
            .transform((builder, typeDescription, classLoader, module) -> builder
                .method(ElementMatchers.named("execute")
                    .or(ElementMatchers.named("executeQuery"))
                    .or(ElementMatchers.named("executeUpdate"))
                )
                .intercept(Advice.to(SqlTimingAdvice.class))
            ).installOn(inst);
        
        System.out.println("Instrumentação de SQL aplicada com sucesso.");
    }
}