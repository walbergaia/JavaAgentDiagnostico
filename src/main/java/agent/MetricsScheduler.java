package agent;

import agent.models.AgentMetrics;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agenda e executa a coleta periódica de métricas.
 */
public class MetricsScheduler {

    private final ConfigLoader config;
    private final MetricsCollector collector;
    private final RestClient restClient;
    private final ScheduledExecutorService scheduler;

    public MetricsScheduler(ConfigLoader config, MetricsCollector collector, RestClient restClient) {
        this.config = config;
        this.collector = collector;
        this.restClient = restClient;
        // Usamos um executor de thread única para garantir que as coletas não se sobreponham.
        // Damos um nome à thread para facilitar a identificação em debuggers e profilers.
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "JavaAgent-MetricsScheduler")
        );
    }

    /**
     * Inicia o agendamento da coleta de métricas.
     */
    public void start() {
        int interval = config.getSamplingIntervalMs();
        System.out.println("Agendando coleta de métricas a cada " + interval + " ms.");

        // A tarefa que será executada periodicamente.
        Runnable collectionTask = () -> {
            try {
                System.out.println("Coletando métricas...");
                AgentMetrics metrics = collector.collect();
                restClient.send(metrics);
            } catch (Exception e) {
                System.err.println("ERRO: Falha inesperada durante a coleta de métricas.");
                e.printStackTrace();
            }
        };

        // Agenda a tarefa para ser executada após um delay inicial de 0,
        // e depois repetidamente no intervalo definido.
        scheduler.scheduleAtFixedRate(collectionTask, 0, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Para o agendador de forma graciosa.
     */
    public void stop() {
        System.out.println("Parando o agendador de métricas...");
        scheduler.shutdown();
        try {
            // Espera um pouco para as tarefas em execução terminarem.
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        System.out.println("Agendador de métricas parado.");
    }
}