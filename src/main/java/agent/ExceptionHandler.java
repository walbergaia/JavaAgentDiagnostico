package agent;

import agent.models.ExceptionInfo;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Captura exceções não tratadas em toda a JVM.
 */
public class ExceptionHandler {

    private final ConfigLoader config;
    // Lista thread-safe para armazenar exceções capturadas entre os ciclos de coleta.
    private final List<ExceptionInfo> capturedExceptions = new CopyOnWriteArrayList<>();

    public ExceptionHandler(ConfigLoader config) {
        this.config = config;
    }

    /**
     * Configura o handler global de exceções.
     */
    public void setup() {
        if (!config.isExceptionCaptureEnabled()) {
            System.out.println("Captura de exceções está desabilitada.");
            return;
        }

        System.out.println("Configurando handler global de exceções.");
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                System.err.println("AGENTE: Exceção não tratada capturada na thread: " + thread.getName());
                
                ExceptionInfo info = new ExceptionInfo();
                info.timestamp = Instant.now().toString();
                info.threadName = thread.getName();
                info.type = throwable.getClass().getSimpleName(); // Nome mais limpo
                info.message = throwable.getMessage() != null ? throwable.getMessage() : "No message";
                info.stackTrace = getStackTraceAsString(throwable, config.isDeepStackAnalysisEnabled());

                capturedExceptions.add(info);
                
                // Log resumo para debugging
                System.err.println("EXCEPTION CAPTURED: " + info.type + " in " + thread.getName() + 
                    " - " + (info.message.length() > 50 ? info.message.substring(0, 50) + "..." : info.message));

                // Opcional: imprimir o stack trace completo no console para manter o comportamento padrão.
                if (config.isDeepStackAnalysisEnabled()) {
                    throwable.printStackTrace();
                }
                
            } catch (Exception e) {
                // Falha no handler de exceção não deve quebrar a aplicação
                System.err.println("AVISO: Erro no handler de exceções: " + e.getMessage());
            }
        });
    }

    /**
     * Coleta as exceções capturadas desde a última chamada e limpa a lista.
     * @return Uma lista de exceções capturadas.
     */
    public List<ExceptionInfo> drainExceptions() {
        if (capturedExceptions.isEmpty()) {
            return new ArrayList<>();
        }
        // "Drena" a lista: copia os itens para uma nova lista e limpa a original.
        List<ExceptionInfo> drained = new ArrayList<>(capturedExceptions);
        capturedExceptions.clear();
        return drained;
    }

    private String getStackTraceAsString(Throwable throwable, boolean deepAnalysis) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        int limit = deepAnalysis ? stackTrace.length : Math.min(stackTrace.length, 10); // Limite de 10 frames por padrão

        pw.println(throwable.toString()); // Imprime a linha da exceção
        for (int i = 0; i < limit; i++) {
            pw.println("\tat " + stackTrace[i]);
        }
        
        if (stackTrace.length > limit) {
            pw.println("\t... " + (stackTrace.length - limit) + " more");
        }
        
        return sw.toString();
    }
}