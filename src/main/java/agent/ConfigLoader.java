package agent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Carrega as configurações do arquivo agent.properties.
 * Implementado como um Singleton para garantir uma única instância.
 */
public class ConfigLoader {

    private static final String CONFIG_FILE = "agent.properties";
    private final Properties properties = new Properties();
    private static ConfigLoader instance;

    /**
     * Construtor privado que carrega o arquivo de propriedades do classpath.
     */
    private ConfigLoader() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                System.err.println("AVISO: Arquivo de configuração não encontrado: " + CONFIG_FILE + ". Usando valores padrão.");
                return;
            }
            properties.load(input);
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
    public static synchronized ConfigLoader getInstance() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
        return instance;
    }

    private String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    private int getIntProperty(String key, int defaultValue) {
        try {
            String value = properties.getProperty(key);
            return (value != null) ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
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
}