package agent.models;

import java.util.List;

/**
 * Informações detalhadas de uma thread incluindo seu stack trace.
 */
public class ThreadStackInfo {
    public long threadId;
    public String threadName;
    public String threadState;
    public String lockName;
    public String lockOwner;
    public long blockedTime;
    public long waitedTime;
    public boolean inNative;
    public boolean suspended;
    public List<String> stackTrace;
    public String timestamp;
}