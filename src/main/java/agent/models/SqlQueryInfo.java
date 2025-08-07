package agent.models;

/**
 * Representa uma única consulta SQL capturada pelo agente.
 */
public class SqlQueryInfo {
    public String timestamp;
    public String query;
    public long durationMs;
    public String error;
    public String threadName;
    public boolean slow;
    public String queryType; // SELECT, INSERT, UPDATE, DELETE, etc.
}