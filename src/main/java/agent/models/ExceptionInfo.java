package agent.models;

/**
 * Representa uma única exceção capturada pelo agente.
 */
public class ExceptionInfo {
    public String timestamp;
    public String type;
    public String message;
    public String stackTrace;
    public String threadName;
}