package at.fhv.visualization;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SearchStatistics {
    private final long sampleInterval;
    private final long maxExpansions;
    private final List<Sample> samples = new ArrayList<>();
    private int totalOperations = 0;
    private boolean stoppedByLimit = false;

    private long lastExpansions = 0;
    private int lastReached = 0;
    private int lastFrontier = 0;
    private int lastMaxDepth = 0;

    private long lastSampledExpansions = -1;

    private BufferedWriter sampleWriter;
    private boolean headerWritten = false;

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

        boolean firstSample = (lastSampledExpansions < 0);
        boolean intervalReached = (expansions - lastSampledExpansions >= sampleInterval);

        if (firstSample || intervalReached) {
            Sample sample = new Sample(expansions, reached, frontier, maxDepth);
            samples.add(sample);
            writeSample(sample);
            lastSampledExpansions = expansions;
        }
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
            sampleWriter.flush();
        } catch (IOException exception) {
            System.err.println("Could not write sample: " + exception.getMessage());
        }
    }

    public static SearchStatistics fromCsv(String path) throws IOException {
        SearchStatistics searchStatistics = new SearchStatistics();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String firstLine = reader.readLine();
            if (firstLine != null && firstLine.startsWith("totalOperations,")) {
                searchStatistics.totalOperations = Integer.parseInt(firstLine.split(",")[1].trim());
            }
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",");
                long expansions = Long.parseLong(parts[0].trim());
                int reached = Integer.parseInt(parts[1].trim());
                int frontier = Integer.parseInt(parts[2].trim());
                int maxDepth = Integer.parseInt(parts[3].trim());
                searchStatistics.samples.add(new Sample(expansions, reached, frontier, maxDepth));
            }
        }
        return searchStatistics;
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

    public long getLastExpansions() {
        return lastExpansions;
    }

    public int getLastReached() {
        return lastReached;
    }

    public int getLastFrontier() {
        return lastFrontier;
    }

    public int getLastMaxDepth() {
        return lastMaxDepth;
    }
}