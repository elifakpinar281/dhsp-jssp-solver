package at.fhv.stats;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class SearchStatistics {
    private final long sampleInterval;
    private final long maxExpansions;
    private int totalOperations = 0;
    private boolean stoppedByLimit = false;

    private long lastExpansions = 0;
    private int lastReached = 0;
    private int lastFrontier = 0;
    private int lastMaxDepth = 0;
    private int peakFrontier = 0;

    private long lastSampledExpansions = -1;

    private BufferedWriter sampleWriter;
    private boolean headerWritten = false;

    private long startNanos = 0;
    private boolean foundSolution = false;
    private int bestMakespanSoFar = Integer.MAX_VALUE;
    private long nanosAtBest = 0;
    private long expansionsAtBest = 0;
    private long deadline = Long.MAX_VALUE;

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
        this.lastExpansions = expansions;
        this.lastReached = reached;
        this.lastFrontier = frontier;
        this.lastMaxDepth = maxDepth;
        if (frontier > peakFrontier) {
            peakFrontier = frontier;
        }

        boolean firstSample = (lastSampledExpansions < 0);
        boolean intervalReached = (expansions - lastSampledExpansions >= sampleInterval);

        if (firstSample || intervalReached) {
            Sample sample = new Sample(expansions, reached, frontier, maxDepth);
            writeSample(sample);
            lastSampledExpansions = expansions;
        }
    }

    public void startTimer() {
        this.startNanos = System.nanoTime();
    }

    public void setTimeLimitMs(long timeLimitMs) {
        if (timeLimitMs > 0) {
            this.deadline = System.nanoTime() + timeLimitMs * 1_000_000;
        } else {
            this.deadline = Long.MAX_VALUE;
        }
    }

    public void reportNewBest(int makespan, long expansions) {
        if (makespan < bestMakespanSoFar) {
            bestMakespanSoFar = makespan;
            nanosAtBest = System.nanoTime();
            expansionsAtBest = lastExpansions;
            foundSolution = true;
        }
    }

    public boolean foundAnySolution() {
        return foundSolution;
    }

    public long getTimeToBestMs() {
        if (!foundSolution) {
            return -1;
        }
        return (nanosAtBest - startNanos) / 1_000_000;
    }

    public long getExpansionsToBest() {
        if (!foundSolution) {
            return -1;
        }
        return expansionsAtBest;
    }

    public void enableLog(String path) throws IOException {
        this.sampleWriter = new BufferedWriter(new FileWriter(path));
    }

    private void writeSample(Sample sample) {
        if (sampleWriter == null) {
            return;
        }
        try {
            if (!headerWritten) {
                sampleWriter.write("totalOperations," + totalOperations);
                sampleWriter.newLine();
                sampleWriter.write("expansions,reached,frontier,maxDepth");
                sampleWriter.newLine();
                headerWritten = true;
            }
            sampleWriter.write(sample.expansions() + "," + sample.reached() + "," + sample.frontier() + "," + sample.maxDepth());
            sampleWriter.newLine();
        } catch (IOException exception) {
            System.err.println("Could not write sample: " + exception.getMessage());
        }
    }

    public void markStoppedByLimit() {
        this.stoppedByLimit = true;
    }

    public boolean isStoppedByLimit() {
        return stoppedByLimit;
    }

    public void closeLog() {
        if (sampleWriter == null) {
            return;
        }
        try {
            sampleWriter.close();
        } catch (IOException exception) {
            System.err.println("Could not close sample log: " + exception.getMessage());
        }
        sampleWriter = null;
    }

    public boolean limitReached(long expansions) {
        if (maxExpansions > 0 && expansions >= maxExpansions) {
            stoppedByLimit = true;
            return true;
        }
        if (System.nanoTime() >= deadline) {
            stoppedByLimit = true;
            return true;
        }
        return false;
    }

    public long getLastExpansions() {
        return lastExpansions;
    }

    public int getLastReached() {
        return lastReached;
    }

    public int getLastMaxDepth() {
        return lastMaxDepth;
    }

    public int getPeakFrontier() {
        return peakFrontier;
    }
}