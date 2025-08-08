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
 * Refatorado para usar uma única cadeia de AgentBuilder com listener unificado.
 */
public class SqlInstrumentation {

    private static boolean isDebugEnabled(ConfigLoader config) {
        // Check for debug configuration or system property
        String debugProp = System.getProperty("agent.sql.debug", "false");
        return "true".equalsIgnoreCase(debugProp) || config.isSqlDebugEnabled();
    }

    public static void setup(Instrumentation inst, ConfigLoader config) {
        if (!config.isSqlCaptureEnabled()) {
            System.out.println("Monitoramento de SQL está desabilitado.");
            return;
        }

        boolean debugEnabled = isDebugEnabled(config);
        
        System.out.println("Aplicando instrumentação unificada em java.sql.Connection/Statement/PreparedStatement...");
        if (debugEnabled) {
            System.out.println("SQL Debug: Modo debug habilitado para instrumentação SQL");
        }

        try {
            // Unified AgentBuilder with consistent configuration and shared listener
            AgentBuilder.Listener listener = debugEnabled ? 
                AgentBuilder.Listener.StreamWriting.toSystemOut() :
                new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onError(String typeName, ClassLoader classLoader, 
                                      net.bytebuddy.utility.JavaModule module, 
                                      boolean loaded, Throwable throwable) {
                        System.err.println("SQL Instrumentation error on " + typeName + ": " + throwable.getMessage());
                    }
                };

            new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)  
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .with(AgentBuilder.InjectionStrategy.UsingReflection.INSTANCE)
                .ignore(ElementMatchers.none())
                .with(listener)
                
                // 1. Instrumenta PreparedStatement - usando hasSuperType para melhor cobertura
                .type(ElementMatchers.hasSuperType(ElementMatchers.named("java.sql.PreparedStatement"))
                    .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
                    if (debugEnabled) {
                        System.out.println("SQL Debug: Instrumentando PreparedStatement: " + typeDescription.getName());
                    }
                    return builder
                        .method(ElementMatchers.named("execute")
                            .or(ElementMatchers.named("executeQuery"))
                            .or(ElementMatchers.named("executeUpdate"))
                        )
                        .intercept(Advice.to(SqlTimingAdvice.class));
                })
                
                // 2. Instrumenta Statement (plain Statement, não PreparedStatement)
                .type(ElementMatchers.hasSuperType(ElementMatchers.named("java.sql.Statement"))
                    .and(ElementMatchers.not(ElementMatchers.hasSuperType(ElementMatchers.named("java.sql.PreparedStatement"))))
                    .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
                    if (debugEnabled) {
                        System.out.println("SQL Debug: Instrumentando Statement: " + typeDescription.getName());
                    }
                    return builder
                        .method(ElementMatchers.named("execute")
                            .or(ElementMatchers.named("executeQuery"))
                            .or(ElementMatchers.named("executeUpdate"))
                            .and(ElementMatchers.takesArgument(0, String.class))) // Captura métodos com String SQL
                        .intercept(Advice.to(StatementTimingAdvice.class));
                })
                
                // 3. Instrumenta Connection.prepareStatement para mapear SQL original
                .type(ElementMatchers.hasSuperType(ElementMatchers.named("java.sql.Connection"))
                    .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
                    if (debugEnabled) {
                        System.out.println("SQL Debug: Instrumentando Connection: " + typeDescription.getName());
                    }
                    return builder
                        .method(ElementMatchers.named("prepareStatement")
                            .and(ElementMatchers.takesArgument(0, String.class)))
                        .intercept(Advice.to(ConnectionPrepareStatementAdvice.class));
                })
                
                .installOn(inst);

        } catch (Exception e) {
            System.err.println("ERRO: Falha ao aplicar instrumentação SQL: " + e.getMessage());
            if (debugEnabled) {
                e.printStackTrace();
            }
            System.err.println("Continuando sem monitoramento de SQL...");
        }
        
        System.out.println("Instrumentação de SQL aplicada com sucesso.");
    }
}