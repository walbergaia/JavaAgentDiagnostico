package agent;

import agent.instrumentation.SqlTimingAdvice;
import agent.instrumentation.StatementTimingAdvice;
import agent.instrumentation.ConnectionPrepareStatementAdvice;
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

    System.out.println("Aplicando instrumentação em java.sql.Connection/Statement/PreparedStatement...");

        try {
            // Instrumenta PreparedStatement executando métodos execute/executeQuery/executeUpdate
            new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .with(AgentBuilder.InjectionStrategy.UsingReflection.INSTANCE)
                .ignore(ElementMatchers.none())
                .type(ElementMatchers.isSubTypeOf(java.sql.PreparedStatement.class)
                    .and(ElementMatchers.not(ElementMatchers.isInterface())))
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
                })
                .with(AgentBuilder.Listener.StreamWriting.toSystemOut())
                .installOn(inst);

            // Instrumenta Statement (para capturar quando app usa Statement ao invés de PreparedStatement)
            new AgentBuilder.Default()
                .type(ElementMatchers.isSubTypeOf(java.sql.Statement.class)
                    .and(ElementMatchers.not(ElementMatchers.isSubTypeOf(java.sql.PreparedStatement.class)))
                    .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builder, td, cl, jm, pd) -> builder
                    .method(ElementMatchers.named("execute").or(ElementMatchers.named("executeQuery")).or(ElementMatchers.named("executeUpdate")))
                    .intercept(Advice.to(StatementTimingAdvice.class))
                )
                .installOn(inst);

            // Instrumenta Connection.prepareStatement para capturar SQL original associada ao PreparedStatement
            new AgentBuilder.Default()
                .type(ElementMatchers.isSubTypeOf(java.sql.Connection.class)
                    .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builder, td, cl, jm, pd) -> builder
                    .method(ElementMatchers.named("prepareStatement").and(ElementMatchers.takesArguments(String.class)))
                    .intercept(Advice.to(ConnectionPrepareStatementAdvice.class))
                )
                .installOn(inst);
        } catch (Exception e) {
            System.err.println("ERRO: Falha ao aplicar instrumentação SQL: " + e.getMessage());
            System.err.println("Continuando sem monitoramento de SQL...");
        }
        
        System.out.println("Instrumentação de SQL aplicada com sucesso.");
    }
}