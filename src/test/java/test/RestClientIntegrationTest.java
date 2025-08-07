package test;

import agent.models.AgentMetrics;
import agent.RestClient;
import agent.ConfigLoader;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Teste com servidor HTTP real para validar envio completo.
 */
public class RestClientIntegrationTest {
    
    private static final AtomicInteger requestCount = new AtomicInteger(0);
    
    public static void main(String[] args) {
        System.out.println("=== Teste de Integração RestClient ===");
        
        HttpServer server = null;
        try {
            // Inicia servidor HTTP simples na porta 8080
            server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/api/v1/metrics", new MetricsHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("✓ Servidor HTTP iniciado em http://localhost:8080");
            
            // Configura e testa RestClient
            ConfigLoader config = ConfigLoader.getInstance();
            RestClient client = new RestClient(config);
            System.out.println("✓ RestClient criado");
            
            // Envia algumas métricas
            for (int i = 0; i < 5; i++) {
                AgentMetrics metrics = createTestMetrics("test-" + i);
                client.send(metrics);
                System.out.println("Métrica enviada: " + i);
                Thread.sleep(1000); // 1 segundo entre envios
            }
            
            // Aguarda processamento
            Thread.sleep(5000);
            
            System.out.println("✓ Teste concluído. Requests recebidos: " + requestCount.get());
            System.out.println("\nStats finais:");
            System.out.println(client.getStats());
            
        } catch (Exception e) {
            System.err.println("ERRO no teste: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (server != null) {
                server.stop(0);
                System.out.println("✓ Servidor HTTP parado");
            }
        }
    }
    
    static class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int count = requestCount.incrementAndGet();
            System.out.println("REQUEST #" + count + " recebido: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            
            // Lê corpo da requisição
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            System.out.println("Tamanho do payload: " + requestBody.length + " bytes");
            
            // Simula resposta de sucesso
            String response = "{\"status\":\"ok\",\"received\":\"" + count + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
            
            System.out.println("Resposta enviada para request #" + count);
        }
    }
    
    private static AgentMetrics createTestMetrics(String suffix) {
        AgentMetrics metrics = new AgentMetrics();
        metrics.timestamp = String.valueOf(System.currentTimeMillis());
        metrics.agentName = "integration-test-agent-" + suffix;
        metrics.application = "test-app";
        metrics.hostname = "test-host";
        metrics.ip = "127.0.0.1";
        metrics.uptime = System.currentTimeMillis();
        
        return metrics;
    }
}