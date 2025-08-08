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

    /**
     * Loga um resumo das principais flags para facilitar diagnóstico de valores divergentes.
     */
    public void logSummary() {
        System.out.println("==== Resumo Config Carregada ====");
        System.out.println("enabled=" + isAgentEnabled());
        System.out.println("config.server.enabled=" + isConfigServerEnabled());
        System.out.println("enable.sql.capture=" + isSqlCaptureEnabled());
        System.out.println("enable.exception.capture=" + isExceptionCaptureEnabled());
        System.out.println("enable.gc.metrics=" + isGcMetricsEnabled());
        System.out.println("enable.system.cpu.mem=" + isSystemCpuMemEnabled());
        System.out.println("enable.thread.stack.analysis=" + isThreadStackAnalysisEnabled());
        System.out.println("tracing.enabled=" + isTracingEnabled());
        System.out.println("profiling.cpu.enabled=" + isCpuProfilingEnabled());
        System.out.println("enable.io.metrics=" + isIoMetricsEnabled());
        System.out.println("io.file.enabled=" + isFileIoEnabled());
        System.out.println("io.socket.enabled=" + isSocketIoEnabled());
        System.out.println("=================================");
    }

    private String getProperty(String key, String defaultValue) {
        // Ordem de precedência: override dinâmico -> system property -> arquivo -> default
        String value = dynamicOverrides.get(key);
        if (value != null) return value;
        String sys = System.getProperty(key);
        if (sys != null) return sys;
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
    
    // --- Configurações de análise de thread stacks ---
    public boolean isThreadStackAnalysisEnabled() { return getBooleanProperty("enable.thread.stack.analysis", false); }
    public int getThreadStackMaxDepth() { return getIntProperty("thread.stack.max.depth", 20); }
    public int getThreadStackSampleSize() { return getIntProperty("thread.stack.sample.size", 10); }
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
    
    // Novas configurações para Enhanced SQL
    public boolean isSqlQueryNormalizationEnabled() { return getBooleanProperty("sql.query.normalization.enabled", true); }
    public boolean isSqlConnectionPoolMonitoringEnabled() { return getBooleanProperty("sql.connection.pool.monitoring.enabled", true); }
    public int getSqlStatsCleanupIntervalSeconds() { return getIntProperty("sql.stats.cleanup.interval.seconds", 300); }
    public int getSqlAggregationMaxEntries() { return getIntProperty("sql.aggregation.max.entries", 200); }
    public boolean isSqlAggregationEnabled() { return getBooleanProperty("sql.aggregation.enabled", true); }
    public boolean isSqlSlowExplainEnabled() { return getBooleanProperty("sql.slow.explain.enabled", false); }
    public int getSqlSlowExplainTimeoutMs() { return getIntProperty("sql.slow.explain.timeout.ms", 500); }

    // --- CPU Sampling Profiler ---
    public boolean isCpuProfilingEnabled() { return getBooleanProperty("profiling.cpu.enabled", false); }
    public int getCpuProfilingIntervalMs() { return getIntProperty("profiling.cpu.interval.ms", 200); }
    public int getCpuProfilingMaxFrames() { return getIntProperty("profiling.cpu.max.frames", 32); }
    public int getCpuProfilingTopN() { return getIntProperty("profiling.cpu.top.n", 20); }
    public boolean isCpuProfilingIncludeNonRunnable() { return getBooleanProperty("profiling.cpu.include.non.runnable", false); }

    // --- I/O File & Socket ---
    public boolean isFileIoEnabled() { return getBooleanProperty("io.file.enabled", true); }
    public boolean isSocketIoEnabled() { return getBooleanProperty("io.socket.enabled", true); }
    public int getIoMaxTopEntries() { return getIntProperty("io.max.top.entries", 10); }

    // --- Tracing ---
    public boolean isTracingEnabled() { return getBooleanProperty("tracing.enabled", false); }
    public boolean isHttpTracingEnabled() { return getBooleanProperty("tracing.http.enabled", true); }
    public double getTracingSampleRate() { 
        try { return Double.parseDouble(getProperty("tracing.sample.rate", "1.0")); } catch (NumberFormatException e) { return 1.0; }
    }
    public int getTracingMaxSpansPerInterval() { return getIntProperty("tracing.max.spans.per.interval", 500); }
    public boolean isW3cPropagationEnabled() { return getBooleanProperty("tracing.propagation.w3c", true); }
    public String getHttpCaptureRequestHeaders() { return getProperty("tracing.http.capture.request.headers", ""); }
    public String getHttpCaptureResponseHeaders() { return getProperty("tracing.http.capture.response.headers", ""); }
    
    // --- SQL Debug Configuration ---
    public boolean isSqlDebugEnabled() { return getBooleanProperty("sql.debug.enabled", false); }
    
    public void logConfigSources() {
        try {
            System.out.println("-- Fontes agent.properties no classpath --");
            java.util.Enumeration<java.net.URL> urls = getClass().getClassLoader().getResources(CONFIG_FILE);
            int i = 0;
            while (urls.hasMoreElements()) {
                java.net.URL u = urls.nextElement();
                System.out.println("[" + (i++) + "] " + u);
            }
            if (i == 0) System.out.println("(nenhum encontrado)");
            System.out.println("----------------------------------");
        } catch (Exception e) {
            System.out.println("Falha ao listar fontes de configuração: " + e.getMessage());
        }
    }
}