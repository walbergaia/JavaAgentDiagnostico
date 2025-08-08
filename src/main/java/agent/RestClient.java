package agent;

import agent.models.AgentMetrics;
import agent.util.JsonUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Responsável por enviar os dados coletados para a API REST.
 * Implementa envio HTTP real com retry automático e backup local.
 */
public class RestClient {

    private final ConfigLoader config;
    private final LocalStorageManager storageManager;
    private final RetryManager retryManager;
    private final DataQueue dataQueue;
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);

    public RestClient(ConfigLoader config) {
        this.config = config;
        this.storageManager = new LocalStorageManager(config);
        this.retryManager = new RetryManager(config);
        this.dataQueue = new DataQueue(config);
        
        // Inicia thread para processar fila e dados pendentes
        startBackgroundProcessor();
    }

    /**
     * Envia as métricas para o endpoint configurado.
     * @param metrics O objeto contendo todas as métricas coletadas.
     */
    public void send(AgentMetrics metrics) {
        if (!config.isRestSendEnabled()) {
            // Se REST está desabilitado, mas storage local está habilitado, armazena localmente
            if (config.isLocalStorageEnabled()) {
                boolean stored = storageManager.store(metrics);
                if (!stored) {
                    System.err.println("ERRO: Falha ao armazenar dados localmente com REST desabilitado");
                }
            }
            return;
        }

        // Adiciona à fila para processamento assíncrono
        if (!dataQueue.enqueue(metrics)) {
            // Se fila cheia, tenta armazenar localmente como fallback
            System.out.println("Fila cheia - tentando armazenar diretamente no storage local");
            boolean stored = storageManager.store(metrics);
            
            if (!stored) {
                System.err.println("ERRO: Dados perdidos - fila cheia E falha no armazenamento local");
            }
        }
    }

    /**
     * Envia dados HTTP para o endpoint configurado.
     * @param metrics Dados a serem enviados
     * @return true se enviado com sucesso
     */
    private boolean sendHttp(AgentMetrics metrics) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(config.getRestEndpointUrl());
            connection = (HttpURLConnection) url.openConnection();
            
            // Configuração da conexão
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "JavaAgentDiagnostico/" + config.getAgentName());
            connection.setDoOutput(true);
            connection.setConnectTimeout(config.getRestTimeoutMs());
            connection.setReadTimeout(config.getRestTimeoutMs());
            
            // Autenticação personalizada se configurada
            String authHeaderName = config.getRestAuthHeaderName();
            String authHeaderValue = config.getRestAuthHeaderValue();
            if (authHeaderName != null && authHeaderValue != null) {
                connection.setRequestProperty(authHeaderName, authHeaderValue);
            }
            
            // Autenticação legacy (auth.token)
            String authToken = config.getAuthToken();
            if (authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + authToken);
            }
            
            // Envia dados JSON
            String jsonPayload = JsonUtil.toJson(metrics);
            try (OutputStream os = connection.getOutputStream();
                 OutputStreamWriter writer = new OutputStreamWriter(os, "UTF-8")) {
                writer.write(jsonPayload);
                writer.flush();
            }
            
            // Verifica resposta
            int responseCode = connection.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                // Sucesso
                retryManager.recordSuccess();
                totalSent.incrementAndGet();
                
                // Log apenas para primeira métrica ou a cada 10 sucessos
                if (totalSent.get() % 10 == 1) {
                    System.out.println("Dados enviados com sucesso para: " + config.getRestEndpointUrl() + 
                        " (Total: " + totalSent.get() + ")");
                }
                return true;
            } else {
                // Erro HTTP
                String errorMsg = String.format("Erro HTTP %d ao enviar dados para %s", 
                    responseCode, config.getRestEndpointUrl());
                    
                // Lê resposta de erro se disponível
                try (InputStream errorStream = connection.getErrorStream()) {
                    if (errorStream != null) {
                        String errorResponse = readInputStream(errorStream);
                        errorMsg += " - " + errorResponse;
                    }
                } catch (Exception e) {
                    // Ignora erro ao ler erro
                }
                
                Exception httpError = new RuntimeException(errorMsg);
                retryManager.recordFailure(httpError);
                totalFailed.incrementAndGet();
                return false;
            }
            
        } catch (Exception e) {
            retryManager.recordFailure(e);
            totalFailed.incrementAndGet();
            
            // Log apenas se for primeira falha ou a cada 10 falhas
            if (totalFailed.get() % 10 == 1) {
                System.err.println("ERRO ao enviar dados: " + e.getMessage() + " (Total falhas: " + totalFailed.get() + ")");
            }
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Processa dados pendentes (arquivos locais) quando conectividade restaurada.
     */
    private void processPendingData() {
        if (!retryManager.shouldProcessPendingData()) {
            return;
        }
        
        List<Path> pendingFiles = storageManager.getPendingFiles();
        if (pendingFiles.isEmpty()) {
            return;
        }
        
        System.out.println("Processando " + pendingFiles.size() + " arquivos pendentes...");
        
        int processed = 0;
        for (Path file : pendingFiles) {
            if (!retryManager.canRetry()) {
                break; // Para se muitas falhas consecutivas
            }
            
            AgentMetrics metrics = storageManager.loadFromFile(file);
            if (metrics != null) {
                if (sendHttp(metrics)) {
                    storageManager.removeFile(file);
                    processed++;
                } else {
                    // Se falhar, para de processar pendentes
                    break;
                }
            } else {
                // Arquivo corrompido, remove
                storageManager.removeFile(file);
            }
            
            // Evita sobrecarregar o servidor
            if (processed % 5 == 0) {
                try {
                    Thread.sleep(100); // 100ms entre grupos de 5
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        if (processed > 0) {
            System.out.println("Processados " + processed + " arquivos pendentes");
        }
    }
    
    /**
     * Thread em background para processar fila e dados pendentes.
     */
    private void startBackgroundProcessor() {
        Thread processor = new Thread(() -> {
            System.out.println("RestClient: Processador em background iniciado");
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Processa dados da fila
                    DataQueue.QueueItem item = dataQueue.dequeueWithTimeout(5000); // 5s timeout
                    
                    if (item != null) {
                        if (retryManager.canRetry()) {
                            boolean success = sendHttp(item.metrics);
                            
                            if (!success) {
                                // Se falhar no envio, tenta armazenar localmente
                                System.out.println("Falha no envio - armazenando localmente como fallback");
                                boolean storedLocally = storageManager.store(item.metrics);
                                
                                if (!storedLocally) {
                                    // Se não conseguir armazenar localmente, tenta recolocar na fila
                                    if (!dataQueue.enqueue(item.metrics, DataQueue.QueueItem.Priority.LOW, "retry")) {
                                        System.err.println("ERRO: Dados perdidos - falha no envio E no armazenamento local E fila cheia");
                                    }
                                }
                            }
                        } else {
                            // Não pode fazer retry agora, volta para fila ou storage
                            if (!dataQueue.enqueue(item.metrics, DataQueue.QueueItem.Priority.LOW, "delayed")) {
                                storageManager.store(item.metrics);
                            }
                        }
                    }
                    
                    // Periodicamente tenta processar dados pendentes
                    if (System.currentTimeMillis() % 30000 < 5000) { // A cada ~30s
                        processPendingData();
                    }
                    
                } catch (Exception e) {
                    System.err.println("ERRO no processador RestClient: " + e.getMessage());
                    try {
                        Thread.sleep(5000); // Pausa em caso de erro
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            System.out.println("RestClient: Processador em background finalizado");
        });
        
        processor.setName("RestClient-Processor");
        processor.setDaemon(true);
        processor.start();
    }
    
    /**
     * Utilitário para ler InputStream como String.
     */
    private String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
        }
        return result.toString().trim();
    }
    
    /**
     * Força reprocessamento de dados pendentes.
     */
    public void forceProcessPendingData() {
        retryManager.forceRetry();
        processPendingData();
    }
    
    /**
     * Retorna estatísticas completas do RestClient.
     */
    public String getStats() {
        return String.format(
            "RestClient Stats:\n" +
            "  Enviados: %d, Falhas: %d\n" +
            "  %s\n" +
            "  %s\n" +
            "  %s",
            totalSent.get(),
            totalFailed.get(),
            retryManager.getRetryStats(),
            dataQueue.getQueueStats(),
            storageManager.getStorageStats()
        );
    }
}