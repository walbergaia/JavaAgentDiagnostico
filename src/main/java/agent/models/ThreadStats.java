package agent.models;

// Estatísticas sobre as Threads da JVM.
public class ThreadStats {
    public int total;
    public int runnable;
    public int blocked;
    public int waiting;
    public int timed_waiting;
    public int deadlocks;
}