package agent.instrumentation;

import agent.ConfigLoader;
import agent.io.IoSocketTracker;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

public class SocketIoInstrumentation {

    public static void setup(Instrumentation inst, ConfigLoader config) {
        if (!config.isSocketIoEnabled()) return;
        try {
            new AgentBuilder.Default()
                .type(ElementMatchers.isSubTypeOf(Socket.class))
                .transform((b, td, cl, m, pd) -> b
                    .method(ElementMatchers.named("connect").and(ElementMatchers.takesArguments(java.net.SocketAddress.class, int.class)))
                    .intercept(Advice.to(ConnectAdvice.class))
                ).installOn(inst);

            new AgentBuilder.Default()
                .type(ElementMatchers.isSubTypeOf(Socket.class))
                .transform((b, td, cl, m, pd) -> b
                    .method(ElementMatchers.named("getInputStream"))
                    .intercept(MethodDelegation.to(SocketGetInputStreamInterceptor.class))
                ).installOn(inst);

            new AgentBuilder.Default()
                .type(ElementMatchers.isSubTypeOf(Socket.class))
                .transform((b, td, cl, m, pd) -> b
                    .method(ElementMatchers.named("getOutputStream"))
                    .intercept(MethodDelegation.to(SocketGetOutputStreamInterceptor.class))
                ).installOn(inst);
            System.out.println("Instrumentação Socket I/O aplicada.");
        } catch (Throwable t) {
            System.err.println("Falha instrumentação Socket I/O: " + t.getMessage());
        }
    }

    private static String remote(Socket s) {
        try {
            if (s.getInetAddress() != null) {
                return s.getInetAddress().getHostAddress() + ":" + s.getPort();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    public static class ConnectAdvice {
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void exit(@Advice.This Socket s, @Advice.Argument(0) java.net.SocketAddress addr) {
            if (addr instanceof InetSocketAddress) {
                IoSocketTracker.get().recordConnect(((InetSocketAddress) addr).getHostString()+":"+((InetSocketAddress) addr).getPort());
            }
        }
    }

    // Interceptores que embrulham streams para contar bytes
    public static class SocketGetInputStreamInterceptor {
        @RuntimeType
        public static Object intercept(@This Socket socket, @SuperCall Callable<InputStream> zuper) throws Exception {
            InputStream original = zuper.call();
            if (original == null) return null;
            final String remote = remote(socket);
            return new InputStream() {
                @Override public int read() throws java.io.IOException {
                    int b = original.read();
                    if (b >= 0) IoSocketTracker.get().recordRead(remote, 1);
                    return b;
                }
                @Override public int read(byte[] b) throws java.io.IOException {
                    int n = original.read(b);
                    if (n > 0) IoSocketTracker.get().recordRead(remote, n);
                    return n;
                }
                @Override public int read(byte[] b, int off, int len) throws java.io.IOException {
                    int n = original.read(b, off, len);
                    if (n > 0) IoSocketTracker.get().recordRead(remote, n);
                    return n;
                }
                @Override public void close() throws java.io.IOException { original.close(); }
                @Override public int available() throws java.io.IOException { return original.available(); }
                @Override public synchronized void mark(int readlimit) { original.mark(readlimit); }
                @Override public synchronized void reset() throws java.io.IOException { original.reset(); }
                @Override public boolean markSupported() { return original.markSupported(); }
                @Override public long skip(long n) throws java.io.IOException { return original.skip(n); }
            };
        }
    }
    public static class SocketGetOutputStreamInterceptor {
        @RuntimeType
        public static Object intercept(@This Socket socket, @SuperCall Callable<OutputStream> zuper) throws Exception {
            OutputStream original = zuper.call();
            if (original == null) return null;
            final String remote = remote(socket);
            return new OutputStream() {
                @Override public void write(int b) throws java.io.IOException {
                    original.write(b);
                    IoSocketTracker.get().recordWrite(remote, 1);
                }
                @Override public void write(byte[] b) throws java.io.IOException {
                    original.write(b);
                    if (b != null) IoSocketTracker.get().recordWrite(remote, b.length);
                }
                @Override public void write(byte[] b, int off, int len) throws java.io.IOException {
                    original.write(b, off, len);
                    if (len > 0) IoSocketTracker.get().recordWrite(remote, len);
                }
                @Override public void flush() throws java.io.IOException { original.flush(); }
                @Override public void close() throws java.io.IOException { original.close(); }
            };
        }
    }
}
