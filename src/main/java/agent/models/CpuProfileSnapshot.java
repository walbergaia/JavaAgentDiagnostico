package agent.models;

import java.util.ArrayList;
import java.util.List;

public class CpuProfileSnapshot {
    public String timestamp;
    public int totalSamples;
    public int uniqueFrames;
    public int discardedSamples;
    public List<HotspotEntry> top = new ArrayList<>();
}
