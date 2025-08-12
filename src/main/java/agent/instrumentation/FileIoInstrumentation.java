package agent.instrumentation;

import agent.ConfigLoader;
import agent.io.IoFileTracker;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.Callable;

public class FileIoInstrumentation {

    public static void setup(Instrumentation inst, ConfigLoader config) {
        if (!config.isFileIoEnabled()) return;
        
        try {
            // Instrument FileInputStream
            new AgentBuilder.Default()
                .type(ElementMatchers.named("java.io.FileInputStream"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                    .constructor(ElementMatchers.takesArguments(File.class))
                    .intercept(Advice.to(FileInputStreamConstructorAdvice.class))
                    .method(ElementMatchers.named("read").and(ElementMatchers.takesNoArguments()))
                    .intercept(Advice.to(FileInputStreamReadAdvice.class))
                    .method(ElementMatchers.named("read").and(ElementMatchers.takesArguments(byte[].class)))
                    .intercept(Advice.to(FileInputStreamReadBytesAdvice.class))
                    .method(ElementMatchers.named("read").and(ElementMatchers.takesArguments(byte[].class, int.class, int.class)))
                    .intercept(Advice.to(FileInputStreamReadBytesOffsetAdvice.class))
                ).installOn(inst);

            // Instrument FileOutputStream
            new AgentBuilder.Default()
                .type(ElementMatchers.named("java.io.FileOutputStream"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                    .constructor(ElementMatchers.takesArguments(File.class))
                    .intercept(Advice.to(FileOutputStreamConstructorAdvice.class))
                    .constructor(ElementMatchers.takesArguments(File.class, boolean.class))
                    .intercept(Advice.to(FileOutputStreamConstructorAppendAdvice.class))
                    .method(ElementMatchers.named("write").and(ElementMatchers.takesArguments(int.class)))
                    .intercept(Advice.to(FileOutputStreamWriteAdvice.class))
                    .method(ElementMatchers.named("write").and(ElementMatchers.takesArguments(byte[].class)))
                    .intercept(Advice.to(FileOutputStreamWriteBytesAdvice.class))
                    .method(ElementMatchers.named("write").and(ElementMatchers.takesArguments(byte[].class, int.class, int.class)))
                    .intercept(Advice.to(FileOutputStreamWriteBytesOffsetAdvice.class))
                ).installOn(inst);

            // Instrument RandomAccessFile
            new AgentBuilder.Default()
                .type(ElementMatchers.named("java.io.RandomAccessFile"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                    .constructor(ElementMatchers.takesArguments(File.class, String.class))
                    .intercept(Advice.to(RandomAccessFileConstructorAdvice.class))
                    .method(ElementMatchers.named("read").and(ElementMatchers.takesNoArguments()))
                    .intercept(Advice.to(RandomAccessFileReadAdvice.class))
                    .method(ElementMatchers.named("read").and(ElementMatchers.takesArguments(byte[].class)))
                    .intercept(Advice.to(RandomAccessFileReadBytesAdvice.class))
                    .method(ElementMatchers.named("read").and(ElementMatchers.takesArguments(byte[].class, int.class, int.class)))
                    .intercept(Advice.to(RandomAccessFileReadBytesOffsetAdvice.class))
                    .method(ElementMatchers.named("write").and(ElementMatchers.takesArguments(int.class)))
                    .intercept(Advice.to(RandomAccessFileWriteAdvice.class))
                    .method(ElementMatchers.named("write").and(ElementMatchers.takesArguments(byte[].class)))
                    .intercept(Advice.to(RandomAccessFileWriteBytesAdvice.class))
                    .method(ElementMatchers.named("write").and(ElementMatchers.takesArguments(byte[].class, int.class, int.class)))
                    .intercept(Advice.to(RandomAccessFileWriteBytesOffsetAdvice.class))
                ).installOn(inst);

            System.out.println("Instrumentação File I/O aplicada.");
        } catch (Throwable t) {
            System.err.println("Falha instrumentação File I/O: " + t.getMessage());
            t.printStackTrace();
        }
    }

    // ThreadLocal to store file paths for each stream
    private static final ThreadLocal<String> currentFilePath = new ThreadLocal<>();

    // FileInputStream Advices
    public static class FileInputStreamConstructorAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit(@Advice.Argument(0) File file) {
            if (file != null) {
                currentFilePath.set(file.getAbsolutePath());
            }
        }
    }

    public static class FileInputStreamReadAdvice {
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void exit(@Advice.Return int bytesRead) {
            String path = currentFilePath.get();
            if (path != null && bytesRead > 0) {
                IoFileTracker.get().recordRead(path, 1);
            }
        }
    }

    public static class FileInputStreamReadBytesAdvice {
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void exit(@Advice.Return int bytesRead) {
            String path = currentFilePath.get();
            if (path != null && bytesRead > 0) {
                IoFileTracker.get().recordRead(path, bytesRead);
            }
        }
    }

    public static class FileInputStreamReadBytesOffsetAdvice {
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void exit(@Advice.Return int bytesRead) {
            String path = currentFilePath.get();
            if (path != null && bytesRead > 0) {
                IoFileTracker.get().recordRead(path, bytesRead);
            }
        }
    }

    // FileOutputStream Advices
    public static class FileOutputStreamConstructorAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit(@Advice.Argument(0) File file) {
            if (file != null) {
                currentFilePath.set(file.getAbsolutePath());
            }
        }
    }

    public static class FileOutputStreamConstructorAppendAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit(@Advice.Argument(0) File file) {
            if (file != null) {
                currentFilePath.set(file.getAbsolutePath());
            }
        }
    }

    public static class FileOutputStreamWriteAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit() {
            String path = currentFilePath.get();
            if (path != null) {
                IoFileTracker.get().recordWrite(path, 1);
            }
        }
    }

    public static class FileOutputStreamWriteBytesAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit(@Advice.Argument(0) byte[] bytes) {
            String path = currentFilePath.get();
            if (path != null && bytes != null) {
                IoFileTracker.get().recordWrite(path, bytes.length);
            }
        }
    }

    public static class FileOutputStreamWriteBytesOffsetAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit(@Advice.Argument(2) int len) {
            String path = currentFilePath.get();
            if (path != null && len > 0) {
                IoFileTracker.get().recordWrite(path, len);
            }
        }
    }

    // RandomAccessFile Advices
    public static class RandomAccessFileConstructorAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit(@Advice.Argument(0) File file) {
            if (file != null) {
                currentFilePath.set(file.getAbsolutePath());
            }
        }
    }

    public static class RandomAccessFileReadAdvice {
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void exit(@Advice.Return int bytesRead) {
            String path = currentFilePath.get();
            if (path != null && bytesRead > 0) {
                IoFileTracker.get().recordRead(path, 1);
            }
        }
    }

    public static class RandomAccessFileReadBytesAdvice {
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void exit(@Advice.Return int bytesRead) {
            String path = currentFilePath.get();
            if (path != null && bytesRead > 0) {
                IoFileTracker.get().recordRead(path, bytesRead);
            }
        }
    }

    public static class RandomAccessFileReadBytesOffsetAdvice {
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void exit(@Advice.Return int bytesRead) {
            String path = currentFilePath.get();
            if (path != null && bytesRead > 0) {
                IoFileTracker.get().recordRead(path, bytesRead);
            }
        }
    }

    public static class RandomAccessFileWriteAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit() {
            String path = currentFilePath.get();
            if (path != null) {
                IoFileTracker.get().recordWrite(path, 1);
            }
        }
    }

    public static class RandomAccessFileWriteBytesAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit(@Advice.Argument(0) byte[] bytes) {
            String path = currentFilePath.get();
            if (path != null && bytes != null) {
                IoFileTracker.get().recordWrite(path, bytes.length);
            }
        }
    }

    public static class RandomAccessFileWriteBytesOffsetAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit(@Advice.Argument(2) int len) {
            String path = currentFilePath.get();
            if (path != null && len > 0) {
                IoFileTracker.get().recordWrite(path, len);
            }
        }
    }
}
