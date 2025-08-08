package agent.instrumentation;

import agent.sql.PreparedStatementRegistry;
import net.bytebuddy.asm.Advice;

import java.sql.Connection;
import java.sql.PreparedStatement;

/** Captura SQL original passada para Connection.prepareStatement(String sql). */
public class ConnectionPrepareStatementAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.This Connection conn,
                            @Advice.Argument(0) String sql,
                            @Advice.Return PreparedStatement ps) {
        try {
            if (ps != null && sql != null) {
                PreparedStatementRegistry.register(ps, sql);
            }
        } catch (Throwable ignored) {}
    }
}
