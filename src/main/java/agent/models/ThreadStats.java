package agent.models;

import java.util.List;
import java.util.ArrayList;

// Estatísticas sobre as Threads da JVM.
public class ThreadStats {
    public int total;
    public int runnable;
    public int blocked;
    public int waiting;
    public int timed_waiting;
    public int deadlocks;
    
    // Detalhes de stack traces por estado (quando habilitado)
    public List<ThreadStackInfo> runnableDetails = new ArrayList<>();
    public List<ThreadStackInfo> blockedDetails = new ArrayList<>();
    public List<ThreadStackInfo> waitingDetails = new ArrayList<>();
    public List<ThreadStackInfo> timedWaitingDetails = new ArrayList<>();
    public List<ThreadStackInfo> deadlockedDetails = new ArrayList<>();
}