package at.fhv;

import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.impl.BeamSearch;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import at.fhv.visualization.SearchStatistics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class BeamWidthSweepExport {
    private static final int[] WIDTHS = {1, 2, 3, 5, 8, 10, 15, 20, 30, 50, 75, 100, 150, 200};

    private record Instance(String key, String label, String path) {}

    private static final List<Instance> INSTANCES = List.of(
            new Instance("demoanlage", "Demoanlage (IM1)", "benchmarks/demoanlage.txt"),
            new Instance("ft06", "FT06 (6x6)", "benchmarks/ft06.txt"),
            new Instance("la02", "LA02 (10x5)", "benchmarks/la02.txt")
    );

    public static void main(String[] args) throws IOException {
        String outPath = args.length > 0 ? args[0] : "tools/beam-sweep.json";

        Parser parser = new Parser();
        ScheduleValidator validator = new ScheduleValidator();

        StringBuilder json = new StringBuilder();
        json.append("{\n");

        for (int idx = 0; idx < INSTANCES.size(); idx++) {
            Instance instance = INSTANCES.get(idx);
            JsspProblem problem = parser.parse(instance.path());

            System.out.println("== " + instance.label() + " ==");
            StringBuilder points = new StringBuilder("[");
            for (int w = 0; w < WIDTHS.length; w++) {
                int width = WIDTHS[w];
                IHeuristic heuristic = new MakespanEstimateHeuristic(problem);
                SearchStatistics stats = new SearchStatistics();
                BeamSearch beamSearch = new BeamSearch(heuristic, width, stats);

                long start = System.currentTimeMillis();
                Schedule schedule = beamSearch.solve(problem);
                long elapsed = System.currentTimeMillis() - start;

                boolean has = schedule != null;
                int makespan = has ? ScheduleValidator.makespan(schedule) : -1;
                boolean valid = has && validator.validateSchedule(problem, schedule).valid();

                System.out.printf(Locale.ROOT, "k=%-4d makespan=%-8s timeMs=%-6d expanded=%-8d valid=%s%n",
                        width, has ? String.valueOf(makespan) : "-", elapsed, stats.getLastExpansions(), valid);

                if (w > 0) points.append(",");
                points.append("{\"k\":").append(width)
                        .append(",\"makespan\":").append(has ? makespan : "null")
                        .append(",\"timeMs\":").append(elapsed)
                        .append(",\"expanded\":").append(stats.getLastExpansions())
                        .append(",\"maxDepth\":").append(stats.getLastMaxDepth())
                        .append(",\"valid\":").append(valid)
                        .append("}");
            }
            points.append("]");

            json.append("  \"").append(instance.key()).append("\": {\n");
            json.append(" \"label\": \"").append(instance.label().replace("\"", "\\\"")).append("\",\n");
            json.append(" \"points\": ").append(points).append("\n");
            json.append("  }");
            if (idx < INSTANCES.size() - 1) json.append(",\n"); else json.append("\n");
        }

        json.append("}\n");
        Files.writeString(Path.of(outPath), json.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote " + outPath);
    }
}