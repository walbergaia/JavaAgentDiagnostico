package agent;

import agent.instrumentation.SqlTimingAdvice;
import agent.instrumentation.StatementTimingAdvice;
import agent.instrumentation.ConnectionPrepareStatementAdvice;
import agent.sql.PreparedStatementParameterRegistry;
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
            // Execução
            new AgentBuilder.Default()
                .type(ElementMatchers.isSubTypeOf(java.sql.PreparedStatement.class)
                    .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builder, td, cl, jm, pd) -> builder
                    .method(ElementMatchers.named("execute")
                        .or(ElementMatchers.named("executeQuery"))
                        .or(ElementMatchers.named("executeUpdate")))
                    .intercept(Advice.to(SqlTimingAdvice.class))
                ).installOn(inst);

            // Setters de parâmetros (assumindo assinatura (int, X))
            new AgentBuilder.Default()
                .type(ElementMatchers.isSubTypeOf(java.sql.PreparedStatement.class)
                    .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builder, td, cl, jm, pd) -> builder
                    .method(ElementMatchers.nameStartsWith("set")
                        .and(ElementMatchers.takesArguments(2))
                        .and(ElementMatchers.takesArgument(0, int.class)))
                    .intercept(Advice.to(ParamSetAdvice.class))
                ).installOn(inst);

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

    /** Advice para interceptar setters de parâmetros em PreparedStatement. */
    public static class ParamSetAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void enter(@Advice.This Object self,
                                 @Advice.Origin("#m") String method,
                                 @Advice.AllArguments Object[] args) {
            try {
                if (!(self instanceof java.sql.PreparedStatement)) return;
                if (args == null || args.length < 2) return;
                int index = (Integer) args[0];
                Object value = args[1];
                PreparedStatementParameterRegistry.set((java.sql.PreparedStatement) self, index, value);
            } catch (Throwable ignored) {}
        }
    }
}