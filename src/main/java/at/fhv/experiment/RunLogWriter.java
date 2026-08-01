package at.fhv.experiment;

import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.solver.impl.tabu.TabuSearch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class RunLogWriter {
    private final Path directory;

    public RunLogWriter() {
        this(Path.of("runs"));
    }

    public RunLogWriter(Path directory) {
        this.directory = directory;
    }

    public Path write(RunLog log) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(log.runId() + ".json");
        Files.write(target, toJson(log).getBytes(StandardCharsets.UTF_8));
        return target;
    }

    private String toJson(RunLog log) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendString(json, "runId", log.runId());
        appendString(json,"timestamp", log.timestamp());
        appendString(json, "instance", log.instance());
        appendString(json,"instanceKey", log.instanceKey());
        appendString(json,"algorithm", log.algorithm());

        json.append(" \"params\": ");
        appendParams(json, log.params());
        json.append(",\n");

        json.append("\"jobCount\": ").append(log.jobCount()).append(",\n");
        json.append("\"machineCount\": ").append(log.machineCount()).append(",\n");
        json.append("\"makespan\": ").append(log.makespan() == null ? "null" : log.makespan()).append(",\n");
        json.append("\"valid\": ").append(log.valid()).append(",\n");
        json.append("\"timeMs\": ").append(log.timeMs()).append(",\n");
        json.append("\"peakHeap\": ").append(log.peakHeap()).append(",\n");
        json.append("\"expanded\": ").append(log.expanded()).append(",\n");
        json.append("\"reached\": ").append(log.reached()).append(",\n");
        json.append("\"maxDepth\": ").append(log.maxDepth()).append(",\n");

        json.append(" \"violations\": ");
        appendViolations(json, log.violations());
        json.append(",\n");

        json.append(" \"iterations\": ");
        appendIterations(json, log.iterations());
        json.append(",\n");

        json.append(" \"schedule\": ");
        appendSchedule(json, log.schedule());
        json.append("\n}\n");
        return json.toString();
    }

    private void appendString(StringBuilder json, String key, String value) {
        json.append(" \"").append(key).append("\": \"").append(escape(value)).append("\",\n");
    }

    private void appendParams(StringBuilder json, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            json.append("{}");
            return;
        }
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) { json.append(", "); }
            first = false;
            json.append("\"").append(escape(entry.getKey())).append("\": ");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) { json.append(value); }
            else { json.append("\"").append(escape(String.valueOf(value))).append("\"");}
        }
        json.append("}");
    }

    private void appendViolations(StringBuilder json, List<String> violations) {
        if (violations == null || violations.isEmpty()) {
            json.append("[]");
            return;
        }
        json.append("[");
        for (int i = 0; i < violations.size(); i++) {
            if (i > 0) { json.append(", "); }
            json.append("\"").append(escape(violations.get(i))).append("\"");
        }
        json.append("]");
    }

    private void appendIterations(StringBuilder json, List<TabuSearch.IterationSnapshot> iterations) {
        if (iterations == null || iterations.isEmpty()) {
            json.append("null");
            return;
        }
        json.append("[");
        for (int i = 0; i < iterations.size(); i++) {
            TabuSearch.IterationSnapshot snapshot = iterations.get(i);
            if (i > 0) { json.append(",");}
            json.append("[").append(snapshot.iteration()).append(",").append(snapshot.makespan()).append(",").append(snapshot.bestMakespan()).append("]");
        }
        json.append("]");
    }

    private void appendSchedule(StringBuilder json, List<ScheduledOperation> schedule) {
        if (schedule == null) {
            json.append("null");
            return;
        }
        json.append("[");
        for (int i = 0; i < schedule.size(); i++) {
            ScheduledOperation operation = schedule.get(i);
            if (i > 0) { json.append(",");}
            json.append("{\"j\":").append(operation.jobId())
                    .append(",\"m\":").append(operation.machineId())
                    .append(",\"s\":").append(operation.startTime())
                    .append(",\"e\":").append(operation.endTime()).append("}");
        }
        json.append("]");
    }

    private String escape(String text) {
        StringBuilder escaped = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default: escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
