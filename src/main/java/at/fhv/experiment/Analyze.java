package at.fhv.experiment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public class Analyze {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) { return; }

        String folder = args[0];
        List<JsonObject> runs = loadRuns(folder);
        if (runs.isEmpty()) { return; }

        StringBuilder report = new StringBuilder();
        report.append("Read ").append(runs.size()).append(" runs from ").append(folder).append("\n");
        appendLowerBoundCheck(report, runs);

        List<String> qualityColumns = List.of("algorithm", "instance", "runs", "feasible_%", "best", "median", "mean", "worst", "spread", "gap_%", "time_median_ms", "eval_mean", "time_to_best_median_ms");
        List<Map<String, Object>> qualityRows = summarizeQuality(runs);
        appendTable(report, "Quality and time (per algorithm/instance)", qualityRows, qualityColumns);

        List<String> speedupColumns = List.of("algorithm", "instance", "seed", "cores", "time_1_core_ms", "time_p_cores_ms", "speedup", "efficiency");
        List<Map<String, Object>> speedupRows = summarizeSpeedup(runs);
        appendTable(report, "Speedup and efficiency (per algorithm/instance/seed)", speedupRows, speedupColumns);

        if (args.length >= 2) {
            writeCsv(args[1], qualityRows, qualityColumns);
            report.append("\nTable 1 written as CSV: ").append(args[1]).append("\n");
        }

        System.out.print(report);
    }

    private static List<JsonObject> loadRuns(String folder) throws IOException {
        List<JsonObject> runs = new ArrayList<>();
        Path dir = Paths.get(folder);
        if (!Files.isDirectory(dir)) { return runs; }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path path : stream) {
                String content = Files.readString(path);
                runs.add(JsonParser.parseString(content).getAsJsonObject());
            }
        }
        return runs;
    }

    private static <K> Map<K, List<JsonObject>> groupBy(List<JsonObject> runs, Function<JsonObject, K> keyFunction) {
        Map<K, List<JsonObject>> groups = new LinkedHashMap<>();
        for (JsonObject run : runs) {
            K key = keyFunction.apply(run);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(run);
        }
        return groups;
    }

    private static String getString(JsonObject run, String field, String fallback) {
        return (run.has(field) && !run.get(field).isJsonNull()) ? run.get(field).getAsString() : fallback;
    }

    private static Double getDouble(JsonObject run, String field) {
        return (run.has(field) && !run.get(field).isJsonNull()) ? run.get(field).getAsDouble() : null;
    }

    private static long getLong(JsonObject run, String field, long fallback) {
        return (run.has(field) && !run.get(field).isJsonNull()) ? run.get(field).getAsLong() : fallback;
    }

    private static boolean getBool(JsonObject run, String field) {
        return run.has(field) && !run.get(field).isJsonNull() && run.get(field).getAsBoolean();
    }

    private static double mean(List<Double> values) {
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int n = sorted.size();
        int mid = n / 2;
        if (n % 2 == 1) { return sorted.get(mid); }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private static double stdev(List<Double> values) {
        double m = mean(values);
        double sumSquares = 0.0;
        for (double v : values) {
            sumSquares += (v - m) * (v - m);
        }
        return Math.sqrt(sumSquares / (values.size() - 1));
    }

    private static double spread(List<Double> values) {
        if (values.size() < 2) { return 0.0; }
        return stdev(values);
    }

    private static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    private static void appendLowerBoundCheck(StringBuilder report, List<JsonObject> runs) {
        List<String> problems = new ArrayList<>();
        for (JsonObject run : runs) {
            Double gap = getDouble(run, "gapPercent");
            if (gap != null && gap < 0) {
                String runId = getString(run, "runId", "?");
                Double makespan = getDouble(run, "makespan");
                Double lowerBound = getDouble(run, "lowerBound");
                problems.add("  " + runId + ": makespan=" + makespan
                        + " lowerBound=" + lowerBound + " gap=" + round(gap, 2) + "%");
            }
        }
        if (!problems.isEmpty()) {
            report.append("\n Invalid lower bound in:\n");
            for (String line : problems) {
                report.append(line).append("\n");
            }
        } else {
            report.append("Lower-bound check:\n");
        }
    }

    private static List<Map<String, Object>> summarizeQuality(List<JsonObject> runs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<List<String>, List<JsonObject>> groups = groupBy(runs,
                r -> List.of(getString(r, "algorithm", ""), getString(r, "instanceKey", "")));

        Map<List<String>, List<JsonObject>> sortedGroups = new TreeMap<>(
                (a, b) -> {
                    int cmp = a.get(0).compareTo(b.get(0));
                    return cmp != 0 ? cmp : a.get(1).compareTo(b.get(1));
                });
        sortedGroups.putAll(groups);

        for (Map.Entry<List<String>, List<JsonObject>> entry : sortedGroups.entrySet()) {
            String algorithm = entry.getKey().get(0);
            String instance = entry.getKey().get(1);
            List<JsonObject> group = entry.getValue();

            int total = group.size();
            List<JsonObject> allowed = new ArrayList<>();
            for (JsonObject run : group) {
                if (getBool(run, "valid")) {
                    allowed.add(run);
                }
            }

            double feasibleRate = 100.0 * allowed.size() / total;

            List<Double> makespans = new ArrayList<>();
            List<Double> gaps = new ArrayList<>();
            for (JsonObject run : allowed) {
                Double makespan = getDouble(run, "makespan");
                if (makespan != null) makespans.add(makespan);
                Double gap = getDouble(run, "gapPercent");
                if (gap != null) gaps.add(gap);
            }

            List<Double> times = new ArrayList<>();
            List<Double> evals = new ArrayList<>();
            List<Double> timeToBest = new ArrayList<>();
            for (JsonObject run : group) {
                times.add((double) getLong(run, "timeMs", 0));
                evals.add((double) getLong(run, "evaluations", 0));
                Double ttb = getDouble(run, "timeToBestMs");
                if (ttb != null) timeToBest.add(ttb);
            }

            Double best = null, medianMakespan = null, meanMakespan = null, worst = null, spreadMakespan = null;
            if (!makespans.isEmpty()) {
                best = java.util.Collections.min(makespans);
                medianMakespan = median(makespans);
                meanMakespan = mean(makespans);
                worst = java.util.Collections.max(makespans);
                spreadMakespan = spread(makespans);
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("algorithm", algorithm);
            row.put("instance", instance);
            row.put("runs", total);
            row.put("feasible_%", round(feasibleRate, 1));
            row.put("best", best);
            row.put("median", medianMakespan);
            row.put("mean", meanMakespan != null ? round(meanMakespan, 1) : null);
            row.put("worst", worst);
            row.put("spread", spreadMakespan != null ? round(spreadMakespan, 1) : null);
            row.put("gap_%", !gaps.isEmpty() ? round(mean(gaps), 2) : null);
            row.put("time_median_ms", round(median(times), 1));
            row.put("eval_mean", !evals.isEmpty() ? Math.round(mean(evals)) : null);
            row.put("time_to_best_median_ms", !timeToBest.isEmpty() ? round(median(timeToBest), 1) : null);
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> summarizeSpeedup(List<JsonObject> runs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<List<Object>, List<JsonObject>> groups = groupBy(runs,
                r -> List.of(getString(r, "algorithm", ""), getString(r, "instanceKey", ""),
                        getLong(r, "seed", 0)));

        Map<List<Object>, List<JsonObject>> sortedGroups = new TreeMap<>((a, b) -> {
            int cmp = ((String) a.get(0)).compareTo((String) b.get(0));
            if (cmp != 0) return cmp;
            cmp = ((String) a.get(1)).compareTo((String) b.get(1));
            if (cmp != 0) return cmp;
            return Long.compare((Long) a.get(2), (Long) b.get(2));
        });
        sortedGroups.putAll(groups);

        for (List<JsonObject> group : sortedGroups.values()) {
            JsonObject oneCoreRun = null;
            for (JsonObject run : group) {
                if (getLong(run, "cores", 1) == 1) {
                    oneCoreRun = run;
                    break;
                }
            }
            if (oneCoreRun == null) {
                continue;
            }

            long timeOneCore = getLong(oneCoreRun, "timeMs", 0);
            for (JsonObject run : group) {
                long cores = getLong(run, "cores", 1);
                if (cores <= 1) {
                    continue;
                }
                long timeManyCores = getLong(run, "timeMs", 0);
                if (timeManyCores <= 0) {
                    continue;
                }
                double speedup = (double) timeOneCore / timeManyCores;
                double efficiency = speedup / cores;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("algorithm", getString(run, "algorithm", ""));
                row.put("instance", getString(run, "instanceKey", ""));
                row.put("seed", getLong(run, "seed", 0));
                row.put("cores", cores);
                row.put("time_1_core_ms", timeOneCore);
                row.put("time_p_cores_ms", timeManyCores);
                row.put("speedup", round(speedup, 2));
                row.put("efficiency", round(efficiency, 2));
                rows.add(row);
            }
        }
        return rows;
    }

    private static void appendTable(StringBuilder report, String title, List<Map<String, Object>> rows, List<String> columns) {
        report.append("\n").append(title).append("\n");
        report.append("-".repeat(title.length())).append("\n");
        if (rows.isEmpty()) {
            report.append("(no data)\n");
            return;
        }

        Map<String, Integer> widths = new LinkedHashMap<>();
        for (String column : columns) {
            int width = column.length();
            for (Map<String, Object> row : rows) {
                width = Math.max(width, cell(row, column).length());
            }
            widths.put(column, width);
        }

        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            report.append(pad(column, widths.get(column)));
            if (i < columns.size() - 1) report.append("  ");
        }
        report.append("\n");

        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                String column = columns.get(i);
                report.append(pad(cell(row, column), widths.get(column)));
                if (i < columns.size() - 1) report.append("  ");
            }
            report.append("\n");
        }
    }

    private static String cell(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? "" : String.valueOf(value);
    }

    private static String pad(String text, int width) {
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static void writeCsv(String path, List<Map<String, Object>> rows, List<String> columns) throws IOException {
        try (Writer writer = new FileWriter(path)) {
            writer.write(String.join(",", columns));
            writer.write("\n");
            for (Map<String, Object> row : rows) {
                List<String> values = new ArrayList<>();
                for (String column : columns) {
                    values.add(csvEscape(cell(row, column)));
                }
                writer.write(String.join(",", values));
                writer.write("\n");
            }
        }
    }

    private static String csvEscape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}