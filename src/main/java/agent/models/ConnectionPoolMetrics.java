package agent.models;

import java.util.Map;
import java.util.HashMap;

/**
 * Métricas de connection pool para monitoramento de performance de banco de dados.
 */
public class ConnectionPoolMetrics {
    
    // Identificação do pool
    public String poolName;
    public String dataSourceClassName;
    public String jdbcUrl;
    public String timestamp;
    
    // Métricas de conexões
    public int totalConnections;
    public int activeConnections;
    public int idleConnections;
    public int pendingConnections;
    
    // Métricas de performance
    public long averageConnectionCreationTimeMs;
    public long averageConnectionAcquisitionTimeMs;
    public long maxConnectionAcquisitionTimeMs;
    public long totalConnectionsCreated;
    public long totalConnectionsDestroyed;
    
    // Métricas de utilização
    public double poolUtilizationPercentage;
    public int maxPoolSize;
    public int minPoolSize;
    
    // Health indicators
    public int failedConnectionAttempts;
    public int connectionTimeouts;
    public int connectionLeaks; // Conexões não fechadas
    
    // Configurações do pool
    public long connectionTimeoutMs;
    public long idleTimeoutMs;
    public long maxLifetimeMs;
    public boolean autoCommit;
    
    // Custom tags
    public Map<String, String> tags = new HashMap<>();
    
    public ConnectionPoolMetrics() {
        this.tags = new HashMap<>();
        this.timestamp = java.time.Instant.now().toString();
    }
    
    /**
     * Calcula a porcentagem de utilização do pool
     */
    public void calculateUtilization() {
        if (maxPoolSize > 0) {
            this.poolUtilizationPercentage = ((double) activeConnections / maxPoolSize) * 100.0;
        }
    }
}