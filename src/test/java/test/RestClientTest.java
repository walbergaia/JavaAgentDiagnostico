package test;

import agent.*;
import agent.models.AgentMetrics;

import java.util.List;

/**
 * Teste simples para validar o sistema completo de envio de dados.
 */
public class RestClientTest {
    
    public static void main(String[] args) {
        System.out.println("=== Teste do Sistema de Envio de Dados ===");
        
        try {
            // Carrega configuração
            ConfigLoader config = ConfigLoader.getInstance();
            System.out.println("✓ ConfigLoader carregado");
            
            // Exibe configurações relevantes
            System.out.println("Configurações:");
            System.out.println("  REST endpoint: " + config.getRestEndpointUrl());
            System.out.println("  REST timeout: " + config.getRestTimeoutMs() + "ms");
            System.out.println("  Retry max attempts: " + config.getRestRetryMaxAttempts());
            System.out.println("  Local storage enabled: " + config.isLocalStorageEnabled());
            System.out.println("  Local storage path: " + config.getLocalStoragePath());
            System.out.println("  Data queue max size: " + config.getDataQueueMaxSize());
            
            // Testa componentes individuais
            testLocalStorageManager(config);
            testRetryManager(config);
            testDataQueue(config);
            testRestClient(config);
            
            System.out.println("\n=== Teste Concluído ===");
            
        } catch (Exception e) {
            System.err.println("ERRO no teste: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testLocalStorageManager(ConfigLoader config) {
        System.out.println("\n--- Teste LocalStorageManager ---");
        
        try {
            LocalStorageManager storage = new LocalStorageManager(config);
            System.out.println("✓ LocalStorageManager criado");
            
            // Cria métricas de teste
            AgentMetrics testMetrics = createTestMetrics();
            
            // Testa armazenamento
            boolean stored = storage.store(testMetrics);
            System.out.println(stored ? "✓ Dados armazenados" : "✗ Falha ao armazenar");
            
            // Testa recuperação
            List<java.nio.file.Path> pendingFiles = storage.getPendingFiles();
            System.out.println("Arquivos pendentes: " + pendingFiles.size());
            
            if (!pendingFiles.isEmpty()) {
                AgentMetrics loaded = storage.loadFromFile(pendingFiles.get(0));
                System.out.println(loaded != null ? "✓ Dados recuperados" : "✗ Falha ao recuperar");
                
                // Limpa arquivo de teste
                storage.removeFile(pendingFiles.get(0));
            }
            
            System.out.println("Stats: " + storage.getStorageStats());
            
        } catch (Exception e) {
            System.err.println("✗ Erro no LocalStorageManager: " + e.getMessage());
        }
    }
    
    private static void testRetryManager(ConfigLoader config) {
        System.out.println("\n--- Teste RetryManager ---");
        
        try {
            RetryManager retry = new RetryManager(config);
            System.out.println("✓ RetryManager criado");
            
            // Testa estado inicial
            System.out.println("Server disponível: " + retry.isServerAvailable());
            System.out.println("Pode fazer retry: " + retry.canRetry());
            
            // Simula falha
            retry.recordFailure(new RuntimeException("Teste de falha"));
            System.out.println("✓ Falha registrada");
            
            // Testa estado após falha
            System.out.println("Server disponível após falha: " + retry.isServerAvailable());
            
            // Simula sucesso
            retry.recordSuccess();
            System.out.println("✓ Sucesso registrado");
            System.out.println("Server disponível após sucesso: " + retry.isServerAvailable());
            
            System.out.println("Stats: " + retry.getRetryStats());
            
        } catch (Exception e) {
            System.err.println("✗ Erro no RetryManager: " + e.getMessage());
        }
    }
    
    private static void testDataQueue(ConfigLoader config) {
        System.out.println("\n--- Teste DataQueue ---");
        
        try {
            DataQueue queue = new DataQueue(config);
            System.out.println("✓ DataQueue criada");
            
            // Testa enfileiramento
            AgentMetrics testMetrics = createTestMetrics();
            boolean enqueued = queue.enqueue(testMetrics);
            System.out.println(enqueued ? "✓ Dados enfileirados" : "✗ Falha ao enfileirar");
            
            // Testa status
            System.out.println("Tamanho da fila: " + queue.size());
            System.out.println("Fila vazia: " + queue.isEmpty());
            
            // Testa desenfileiramento
            DataQueue.QueueItem item = queue.dequeue();
            System.out.println(item != null ? "✓ Dados desenfileirados" : "✗ Falha ao desenfileirar");
            
            if (item != null) {
                System.out.println("Prioridade: " + item.priority);
                System.out.println("Fonte: " + item.source);
            }
            
            System.out.println("Stats: " + queue.getQueueStats());
            
        } catch (Exception e) {
            System.err.println("✗ Erro no DataQueue: " + e.getMessage());
        }
    }
    
    private static void testRestClient(ConfigLoader config) {
        System.out.println("\n--- Teste RestClient ---");
        
        try {
            RestClient client = new RestClient(config);
            System.out.println("✓ RestClient criado");
            
            // Aguarda um pouco para thread inicializar
            Thread.sleep(1000);
            
            // Testa envio (vai falhar pois endpoint não existe)
            AgentMetrics testMetrics = createTestMetrics();
            client.send(testMetrics);
            System.out.println("✓ Dados enviados para processamento");
            
            // Aguarda processamento
            Thread.sleep(3000);
            
            System.out.println("Stats: " + client.getStats());
            
        } catch (Exception e) {
            System.err.println("✗ Erro no RestClient: " + e.getMessage());
        }
    }
    
    private static AgentMetrics createTestMetrics() {
        AgentMetrics metrics = new AgentMetrics();
        metrics.timestamp = String.valueOf(System.currentTimeMillis());
        metrics.agentName = "test-agent";
        metrics.application = "test-app";
        metrics.hostname = "test-host";
        metrics.ip = "127.0.0.1";
        metrics.uptime = System.currentTimeMillis();
        
        return metrics;
    }
}