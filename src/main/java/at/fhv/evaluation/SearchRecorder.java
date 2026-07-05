package at.fhv.evaluation;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;

public class SearchRecorder {
    private final String csvPath;
    private final long sampleInterval;
    private BufferedWriter writer;

    public SearchRecorder() {
        this(null, 0);
    }

    public SearchRecorder(String csvPath, long sampleInterval) {
        this.csvPath = csvPath;
        this.sampleInterval = sampleInterval;
    }

    public void start() {
        if (csvPath == null) {
            return;
        }

        try {
            writer = new BufferedWriter(new FileWriter(csvPath));
            writer.write("expansions,reached,frontier,max_depth,total_ops");
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException("CSV could not be opened." + csvPath, exception);
        }
    }

    public void sample(long expansions, int reached, int frontier, int depth, int totalOps) {
        if (writer == null || sampleInterval <= 0) {
            return;
        }
        if (expansions % sampleInterval == 0) {
            writeRow(expansions, reached, frontier, depth, totalOps);
        }
    }

    public void record(long expansions, int reached, int frontier, int depth, int totalOps) {
        writeRow(expansions, reached, frontier, depth, totalOps);
    }

    public void finish() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException exception) {
            throw new UncheckedIOException("CSV could not be closed", exception);
        }
    }

    private void writeRow(long expansions, int reached, int frontier, int depth, int totalOps) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(expansions + "," + reached + "," + frontier + "," + depth + "," + totalOps);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Row could not be written", e);
        }
    }
}
