package agent.models;

/**
 * Representa um hotspot de CPU agregado por método (stack frame top ou caminho completo).
 */
public class HotspotEntry {
    public String symbol; // class.method:line ou caminho simplificado
    public int samples;
    public int selfSamples;
    public double pct; // percentual relativo (calculado no flush)
}
