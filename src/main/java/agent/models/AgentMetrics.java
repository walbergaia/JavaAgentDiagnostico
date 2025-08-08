package agent.models;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Classe principal que agrega todas as métricas coletadas em um único ciclo.
public class AgentMetrics {
    public String timestamp;
    public String agentName;
    public String application;
    public String hostname;
    public String ip;
    public long uptime;
    public HeapMetrics heap;
    public CpuMetrics cpu;
    public MemoryMetrics memory;
    public ThreadStats threads;
    public GcMetrics gc;
    // Usamos listas thread-safe para que possam ser preenchidas por diferentes partes do agente.
    public final List<ExceptionInfo> exceptions = new CopyOnWriteArrayList<>();
    public final List<SqlQueryInfo> sql = new CopyOnWriteArrayList<>();
    public final List<ConnectionPoolMetrics> connectionPools = new CopyOnWriteArrayList<>();
    public final List<TraceSpan> spans = new CopyOnWriteArrayList<>();
    public CpuProfileSnapshot cpuProfile; // snapshot opcional de CPU sampling
    public IoMetrics io; // métricas de I/O
}