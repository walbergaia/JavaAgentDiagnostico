package agent.config;

import agent.ConfigLoader;
import agent.util.JsonUtil;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP para configuração dinâmica do JavaAgentDiagnostico.
 * Permite consultar e atualizar configurações em tempo de execução.
 */
public class ConfigurationServer {

    private final ConfigLoader configLoader;
    private final HttpServer server;
    private final String authToken;

    public ConfigurationServer(ConfigLoader configLoader) throws IOException {
        this.configLoader = configLoader;
        this.authToken = configLoader.getConfigServerAuthToken();
        
        String bindAddress = configLoader.getConfigServerBindAddress();
        int port = configLoader.getConfigServerPort();
        
        this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        setupHandlers();
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    private void setupHandlers() {
        server.createContext("/config", new ConfigHandler());
        server.createContext("/config/reload", new ReloadHandler());
        server.createContext("/health", new HealthHandler());
    }

    public void start() {
        server.start();
        System.out.println("Servidor de configuração iniciado em " + 
            server.getAddress().getHostString() + ":" + server.getAddress().getPort());
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("Servidor de configuração parado.");
        }
    }

    private boolean isAuthenticationRequired() {
        return authToken != null && !authToken.trim().isEmpty();
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        if (!isAuthenticationRequired()) {
            return true;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authHeader.substring(7);
        return authToken.equals(token);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("status", statusCode);
        String response = JsonUtil.toJson(error);
        sendResponse(exchange, statusCode, response);
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder body = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    /**
     * Handler para GET /config e POST /config
     */
    private class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Verificação de autenticação
            if (!isAuthenticated(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            String method = exchange.getRequestMethod();
            
            try {
                if ("GET".equals(method)) {
                    handleGetConfig(exchange);
                } else if ("POST".equals(method)) {
                    handlePostConfig(exchange);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                System.err.println("Erro no ConfigHandler: " + e.getMessage());
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        private void handleGetConfig(HttpExchange exchange) throws IOException {
            Properties allProperties = configLoader.getAllProperties();
            Map<String, Object> response = new HashMap<>();
            
            for (String key : allProperties.stringPropertyNames()) {
                response.put(key, allProperties.getProperty(key));
            }
            
            String json = JsonUtil.toJson(response);
            sendResponse(exchange, 200, json);
            
            System.out.println("Configurações consultadas via HTTP");
        }

        private void handlePostConfig(HttpExchange exchange) throws IOException {
            String requestBody = readRequestBody(exchange);
            
            if (requestBody.trim().isEmpty()) {
                sendError(exchange, 400, "Request body is required");
                return;
            }

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> updates = JsonUtil.fromJson(requestBody, Map.class);
                
                Map<String, String> updated = new HashMap<>();
                Map<String, String> errors = new HashMap<>();
                
                for (Map.Entry<String, Object> entry : updates.entrySet()) {
                    String key = entry.getKey();
                    String value = String.valueOf(entry.getValue());
                    
                    try {
                        // Validação básica de valores
                        if (validateConfigValue(key, value)) {
                            configLoader.setProperty(key, value);
                            updated.put(key, value);
                        } else {
                            errors.put(key, "Invalid value: " + value);
                        }
                    } catch (Exception e) {
                        errors.put(key, "Error updating: " + e.getMessage());
                    }
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("updated", updated);
                if (!errors.isEmpty()) {
                    response.put("errors", errors);
                }
                
                String json = JsonUtil.toJson(response);
                sendResponse(exchange, 200, json);
                
                System.out.println("Configurações atualizadas via HTTP: " + updated.keySet());
                
            } catch (Exception e) {
                sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
            }
        }

        private boolean validateConfigValue(String key, String value) {
            // Validações básicas por tipo de configuração
            if (key.endsWith(".enabled") || key.equals("enabled")) {
                return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            }
            
            if (key.endsWith(".ms") || key.endsWith(".port")) {
                try {
                    int intValue = Integer.parseInt(value);
                    return intValue >= 0;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            
            // Para outros tipos, aceita qualquer string não vazia
            return value != null && !value.trim().isEmpty();
        }
    }

    /**
     * Handler para POST /config/reload
     */
    private class ReloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                configLoader.reloadFromFile();
                
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Configuration reloaded from file");
                response.put("timestamp", java.time.Instant.now().toString());
                
                String json = JsonUtil.toJson(response);
                sendResponse(exchange, 200, json);
                
                System.out.println("Configurações recarregadas do arquivo via HTTP");
                
            } catch (Exception e) {
                System.err.println("Erro ao recarregar configurações: " + e.getMessage());
                sendError(exchange, 500, "Error reloading configuration: " + e.getMessage());
            }
        }
    }

    /**
     * Handler para GET /health
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", java.time.Instant.now().toString());
            health.put("configServer", "running");
            health.put("agentEnabled", configLoader.isAgentEnabled());
            
            String json = JsonUtil.toJson(health);
            sendResponse(exchange, 200, json);
        }
    }
}