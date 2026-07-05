package at.fhv.visualization;


import java.util.ArrayList;
import java.util.List;

public class SearchStatistics {
    public record Sample(long expansions, int reached, int frontier, int maxDepth) {}

    private final long sampleInterval;
    private final long maxExpansions;

    private final List<Sample> samples = new ArrayList<>();
    private int totalOperations = 0;
    private boolean stoppedByLimit = false;

    public SearchStatistics() {
        this(1000, 0);
    }

    public SearchStatistics(long sampleInterval, long maxExpansions) {
        this.sampleInterval = Math.max(1, sampleInterval);
        this.maxExpansions = maxExpansions;
    }

    public void setTotalOperations(int totalOperations) {
        this.totalOperations = totalOperations;
    }

    public void record(long expansions, int reached, int frontier, int maxDepth) {
        if (expansions % sampleInterval == 0) {
            samples.add(new Sample(expansions, reached, frontier, maxDepth));
        }
    }

    public boolean limitReached(long expansions) {
        if (maxExpansions <= 0) {
            return false;
        }
        if (expansions >= maxExpansions) {
            stoppedByLimit = true;
            return true;
        }
        return false;
    }

    public List<Sample> getSamples() {
        return samples;
    }

    public int getTotalOperations() {
        return totalOperations;
    }

    public boolean wasStoppedByLimit() {
        return stoppedByLimit;
    }
}
