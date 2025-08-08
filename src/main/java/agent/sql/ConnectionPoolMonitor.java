package agent.sql;

import agent.models.ConnectionPoolMetrics;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.MBeanServerFactory;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Monitor de connection pools que coleta métricas de pools de conexão populares.
 */
public class ConnectionPoolMonitor {
    
    private final MBeanServer mBeanServer;
    
    public ConnectionPoolMonitor() {
        this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
    }
    
    /**
     * Coleta métricas de todos os connection pools detectados.
     * 
     * @return Lista de métricas de connection pools
     */
    public List<ConnectionPoolMetrics> collectAllPoolMetrics() {
        List<ConnectionPoolMetrics> allMetrics = new ArrayList<>();
        
        // HikariCP
        allMetrics.addAll(collectHikariCPMetrics());
        
        // Tomcat JDBC Pool
        allMetrics.addAll(collectTomcatPoolMetrics());
        
        // Apache DBCP
        allMetrics.addAll(collectDBCPMetrics());
        
        // C3P0
        allMetrics.addAll(collectC3P0Metrics());
        
        return allMetrics;
    }
    
    /**
     * Coleta métricas do HikariCP via JMX.
     */
    private List<ConnectionPoolMetrics> collectHikariCPMetrics() {
        List<ConnectionPoolMetrics> metrics = new ArrayList<>();
        
        try {
            Set<ObjectName> hikariPools = mBeanServer.queryNames(
                new ObjectName("com.zaxxer.hikari:type=Pool (*)")
                , null);
            
            for (ObjectName poolName : hikariPools) {
                ConnectionPoolMetrics poolMetrics = new ConnectionPoolMetrics();
                
                try {
                    poolMetrics.poolName = poolName.getKeyProperty("Pool");
                    poolMetrics.dataSourceClassName = "HikariCP";
                    
                    // Métricas básicas
                    poolMetrics.totalConnections = getIntAttribute(poolName, "TotalConnections");
                    poolMetrics.activeConnections = getIntAttribute(poolName, "ActiveConnections");
                    poolMetrics.idleConnections = getIntAttribute(poolName, "IdleConnections");
                    poolMetrics.pendingConnections = getIntAttribute(poolName, "ThreadsAwaitingConnection");
                    
                    // Configurações
                    poolMetrics.maxPoolSize = getIntAttribute(poolName, "MaximumPoolSize");
                    poolMetrics.minPoolSize = getIntAttribute(poolName, "MinimumIdle");
                    
                    // Métricas avançadas (se disponíveis)\n                    poolMetrics.totalConnectionsCreated = getLongAttribute(poolName, "TotalConnectionsCreated");
                    
                    poolMetrics.calculateUtilization();
                    poolMetrics.tags.put("pool_type", "HikariCP");
                    
                    metrics.add(poolMetrics);
                } catch (Exception e) {
                    System.err.println("Erro ao coletar métricas do HikariCP pool: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // HikariCP não está disponível ou não configurado
        }
        
        return metrics;
    }
    
    /**
     * Coleta métricas do Tomcat JDBC Pool via JMX.
     */
    private List<ConnectionPoolMetrics> collectTomcatPoolMetrics() {
        List<ConnectionPoolMetrics> metrics = new ArrayList<>();
        
        try {
            Set<ObjectName> tomcatPools = mBeanServer.queryNames(
                new ObjectName("tomcat.jdbc:type=ConnectionPool,name=*")
                , null);
            
            for (ObjectName poolName : tomcatPools) {
                ConnectionPoolMetrics poolMetrics = new ConnectionPoolMetrics();
                
                try {
                    poolMetrics.poolName = poolName.getKeyProperty("name");
                    poolMetrics.dataSourceClassName = "Tomcat JDBC Pool";
                    
                    poolMetrics.activeConnections = getIntAttribute(poolName, "NumActive");
                    poolMetrics.idleConnections = getIntAttribute(poolName, "NumIdle");
                    poolMetrics.maxPoolSize = getIntAttribute(poolName, "MaxActive");
                    
                    poolMetrics.totalConnections = poolMetrics.activeConnections + poolMetrics.idleConnections;
                    poolMetrics.calculateUtilization();
                    poolMetrics.tags.put("pool_type", "Tomcat");
                    
                    metrics.add(poolMetrics);
                } catch (Exception e) {
                    System.err.println("Erro ao coletar métricas do Tomcat pool: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // Tomcat não está disponível
        }
        
        return metrics;
    }
    
    /**
     * Coleta métricas do Apache DBCP via JMX.
     */
    private List<ConnectionPoolMetrics> collectDBCPMetrics() {
        List<ConnectionPoolMetrics> metrics = new ArrayList<>();
        
        try {
            Set<ObjectName> dbcpPools = mBeanServer.queryNames(
                new ObjectName("org.apache.commons.dbcp2:type=BasicDataSource,name=*")
                , null);
            
            for (ObjectName poolName : dbcpPools) {
                ConnectionPoolMetrics poolMetrics = new ConnectionPoolMetrics();
                
                try {
                    poolMetrics.poolName = poolName.getKeyProperty("name");
                    poolMetrics.dataSourceClassName = "Apache DBCP2";
                    
                    poolMetrics.activeConnections = getIntAttribute(poolName, "NumActive");
                    poolMetrics.idleConnections = getIntAttribute(poolName, "NumIdle");
                    poolMetrics.maxPoolSize = getIntAttribute(poolName, "MaxTotal");
                    
                    poolMetrics.totalConnections = poolMetrics.activeConnections + poolMetrics.idleConnections;
                    poolMetrics.calculateUtilization();
                    poolMetrics.tags.put("pool_type", "DBCP2");
                    
                    metrics.add(poolMetrics);
                } catch (Exception e) {
                    System.err.println("Erro ao coletar métricas do DBCP pool: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // DBCP não está disponível
        }
        
        return metrics;
    }
    
    /**
     * Coleta métricas do C3P0 via JMX.
     */
    private List<ConnectionPoolMetrics> collectC3P0Metrics() {
        List<ConnectionPoolMetrics> metrics = new ArrayList<>();
        
        try {
            Set<ObjectName> c3p0Pools = mBeanServer.queryNames(
                new ObjectName("com.mchange.v2.c3p0:type=PooledDataSource,*")
                , null);
            
            for (ObjectName poolName : c3p0Pools) {
                ConnectionPoolMetrics poolMetrics = new ConnectionPoolMetrics();
                
                try {
                    poolMetrics.poolName = poolName.getKeyProperty("name");
                    poolMetrics.dataSourceClassName = "C3P0";
                    
                    poolMetrics.activeConnections = getIntAttribute(poolName, "NumConnectionsDefaultUser");
                    poolMetrics.idleConnections = getIntAttribute(poolName, "NumIdleConnectionsDefaultUser");
                    poolMetrics.maxPoolSize = getIntAttribute(poolName, "MaxPoolSizeDefaultUser");
                    
                    poolMetrics.totalConnections = poolMetrics.activeConnections + poolMetrics.idleConnections;
                    poolMetrics.calculateUtilization();
                    poolMetrics.tags.put("pool_type", "C3P0");
                    
                    metrics.add(poolMetrics);
                } catch (Exception e) {
                    System.err.println("Erro ao coletar métricas do C3P0 pool: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // C3P0 não está disponível
        }
        
        return metrics;
    }
    
    private int getIntAttribute(ObjectName objectName, String attributeName) {
        try {
            Object value = mBeanServer.getAttribute(objectName, attributeName);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception e) {
            // Atributo não disponível
        }
        return -1;
    }
    
    private long getLongAttribute(ObjectName objectName, String attributeName) {
        try {
            Object value = mBeanServer.getAttribute(objectName, attributeName);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception e) {
            // Atributo não disponível
        }
        return -1;
    }
}