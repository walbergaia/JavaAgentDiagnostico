package agent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Carrega as configurações do arquivo agent.properties.
 * Implementado como um Singleton para garantir uma única instância.
 * Suporta atualizações dinâmicas thread-safe.
 */
public class ConfigLoader {

    private static final String CONFIG_FILE = "agent.properties";
    private final AtomicReference<Properties> properties = new AtomicReference<>(new Properties());
    private final ConcurrentHashMap<String, String> dynamicOverrides = new ConcurrentHashMap<>();
    private static volatile ConfigLoader instance;

    /**
     * Construtor privado que carrega o arquivo de propriedades do classpath.
     */
    private ConfigLoader() {
        loadFromFile();
    }

    /**
     * Carrega configurações do arquivo agent.properties.
     */
    private void loadFromFile() {
        Properties newProperties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                System.err.println("AVISO: Arquivo de configuração não encontrado: " + CONFIG_FILE + ". Usando valores padrão.");
                return;
            }
            newProperties.load(input);
            properties.set(newProperties);
            System.out.println("Configuração carregada de " + CONFIG_FILE);
        } catch (IOException ex) {
            System.err.println("ERRO: Falha ao carregar o arquivo de configuração " + CONFIG_FILE + ". Usando valores padrão.");
            ex.printStackTrace();
        }
    }

    /**
     * Retorna a instância única do ConfigLoader.
     *
     * @return A instância do ConfigLoader.
     */
    public static ConfigLoader getInstance() {
        if (instance == null) {
            synchronized (ConfigLoader.class) {
                if (instance == null) {
                    instance = new ConfigLoader();
                }
            }
        }
        return instance;
    }

    /**
     * Recarrega configurações do arquivo agent.properties.
     * Remove todas as configurações dinâmicas.
     */
    public synchronized void reloadFromFile() {
        System.out.println("Recarregando configurações do arquivo " + CONFIG_FILE);
        dynamicOverrides.clear();
        loadFromFile();
    }

    /**
     * Atualiza uma configuração dinamicamente.
     * @param key Chave da configuração
     * @param value Novo valor
     */
    public void setProperty(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key e value não podem ser null");
        }
        dynamicOverrides.put(key, value);
        System.out.println("Configuração dinâmica atualizada: " + key + " = " + value);
    }

    /**
     * Remove uma configuração dinâmica, voltando ao valor do arquivo.
     * @param key Chave da configuração
     */
    public void removeProperty(String key) {
        if (key != null) {
            dynamicOverrides.remove(key);
            System.out.println("Configuração dinâmica removida: " + key);
        }
    }

    /**
     * Retorna todas as configurações atuais (arquivo + dinâmicas).
     * @return Properties com todas as configurações
     */
    public Properties getAllProperties() {
        Properties allProps = new Properties();
        allProps.putAll(properties.get());
        allProps.putAll(dynamicOverrides);
        return allProps;
    }

    private String getProperty(String key, String defaultValue) {
        // Primeiro verifica override dinâmico, depois arquivo
        String value = dynamicOverrides.get(key);
        if (value != null) {
            return value;
        }
        return properties.get().getProperty(key, defaultValue);
    }

    private int getIntProperty(String key, int defaultValue) {
        try {
            String value = getProperty(key, null);
            return (value != null) ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key, null);
        return (value != null) ? Boolean.parseBoolean(value) : defaultValue;
    }

    // --- Métodos de acesso para cada configuração ---

    public boolean isAgentEnabled() { return getBooleanProperty("enabled", true); }
    public String getAgentName() { return getProperty("agent.name", "diagnostic-agent"); }
    public String getApplicationName() { return getProperty("application.name", "default-app"); }
    public int getSamplingIntervalMs() { return getIntProperty("sampling.interval.ms", 30000); }
    public boolean isRestSendEnabled() { return getBooleanProperty("send.rest.enabled", true); }
    public String getRestEndpointUrl() { return getProperty("rest.endpoint.url", "http://localhost:8080/api/v1/metrics"); }
    public String getAuthToken() { return getProperty("auth.token", null); }
    public boolean isExceptionCaptureEnabled() { return getBooleanProperty("enable.exception.capture", true); }
    public boolean isSqlCaptureEnabled() { return getBooleanProperty("enable.sql.capture", true); }
    public int getSqlSlowThresholdMs() { return getIntProperty("sql.slow.threshold.ms", 1000); }
    public boolean isDeepStackAnalysisEnabled() { return getBooleanProperty("enable.deep.stack.analysis", false); }
    public boolean isGcMetricsEnabled() { return getBooleanProperty("enable.gc.metrics", true); }
    public boolean isSystemCpuMemEnabled() { return getBooleanProperty("enable.system.cpu.mem", true); }
    public boolean isIoMetricsEnabled() { return getBooleanProperty("enable.io.metrics", false); }

    // --- Configurações REST avançadas ---
    public int getRestTimeoutMs() { return getIntProperty("rest.timeout.ms", 5000); }
    public int getRestRetryMaxAttempts() { return getIntProperty("rest.retry.max.attempts", 3); }
    public int getRestRetryBackoffInitialMs() { return getIntProperty("rest.retry.backoff.initial.ms", 1000); }
    public String getRestAuthHeaderName() { return getProperty("rest.auth.header.name", null); }
    public String getRestAuthHeaderValue() { return getProperty("rest.auth.header.value", null); }

    // --- Configurações de armazenamento local ---
    public boolean isLocalStorageEnabled() { return getBooleanProperty("local.storage.enabled", true); }
    public String getLocalStoragePath() { return getProperty("local.storage.path", System.getProperty("java.io.tmpdir") + "/javaagent-diagnostico"); }
    public int getLocalStorageMaxFiles() { return getIntProperty("local.storage.max.files", 1000); }
    public int getLocalStorageCleanupHours() { return getIntProperty("local.storage.cleanup.hours", 24); }

    // --- Configurações da fila de dados ---
    public int getDataQueueMaxSize() { return getIntProperty("data.queue.max.size", 10000); }

    // --- Configurações do servidor de configuração dinâmica ---
    public boolean isConfigServerEnabled() { return getBooleanProperty("config.server.enabled", false); }
    public int getConfigServerPort() { return getIntProperty("config.server.port", 8090); }
    public String getConfigServerAuthToken() { return getProperty("config.server.auth.token", null); }
    public String getConfigServerBindAddress() { return getProperty("config.server.bind.address", "localhost"); }
}