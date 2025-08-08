package agent.models;

import java.util.ArrayList;
import java.util.List;

public class IoMetrics {
    public long totalFileReadBytes;
    public long totalFileWriteBytes;
    public long totalSocketReadBytes;
    public long totalSocketWriteBytes;
    public List<IoFileStat> topFiles = new ArrayList<>();
    public List<IoSocketStat> topSockets = new ArrayList<>();
}
