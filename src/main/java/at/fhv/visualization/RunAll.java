package at.fhv.visualization;

import at.fhv.parser.GeneralParser;
import at.fhv.parser.Parser;
import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.impl.decoder.RandomStartDecoder;
import at.fhv.solver.impl.BeamSearch;
import at.fhv.solver.impl.GreedySearch;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;
import at.fhv.solver.impl.TabuSearch;
import at.fhv.solver.impl.tabu.impl.N5Neighbourhood;
import at.fhv.solver.validation.MemorySampler;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RunAll {
    private static final long GREEDY_MAX_EXPANSIONS = 400_000;
    private static final int BEAM_WIDTH = 20;
    private static final int TABU_TENURE = 10;

    private record InstanceSpec(String key, String label, String path, boolean general, int tabuNoImprove) {}

    public static void main(String[] args) throws IOException {
        InstanceSpec[] specs = new InstanceSpec[]{
                new InstanceSpec("demoanlage", "Demoanlage (IM1)", "demoanlage.txt", true, 150),
                new InstanceSpec("ft06", "FT06 (6x6)", "benchmarks/ft06.txt", false, 400),
                new InstanceSpec("la02", "LA02 (10x5)", "benchmarks/la02.txt", false, 400),
        };
        String[] algorithms = {"GREEDY", "BEAM", "TABU"};

        StringBuilder json = new StringBuilder();
        json.append("{\n  \"instances\": {\n");

        for (int s = 0; s < specs.length; s++) {
            InstanceSpec spec = specs[s];
            JsspProblem problem = spec.general()
                    ? new GeneralParser().parse(spec.path())
                    : new Parser().parse(spec.path());

            System.out.println("== Instanz: " + spec.label() + " ==");

            json.append(" \"").append(spec.key()).append("\": {\n");
            json.append(" \"label\": \"").append(esc(spec.label())).append("\",\n");
            json.append(" \"jobCount\": ").append(problem.getJobs().size()).append(",\n");
            json.append(" \"machineCount\": ").append(problem.getMachines().size()).append(",\n");
            json.append(" \"results\": {\n");

            for (int a = 0; a < algorithms.length; a++) {
                String algo = algorithms[a];
                Map<String, Object> res = runOne(algo, problem, spec);
                appendResult(json, algo, res);
                if (a < algorithms.length - 1) json.append(",");
                json.append("\n");
            }
            json.append(" }\n");
            json.append(" }");
            if (s < specs.length - 1) json.append(",");
            json.append("\n");
        }

        json.append("  }\n}\n");
        Files.write(Path.of("results.json"), json.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("results.json written (" + json.length() + " chars)");
    }

    private static Map<String, Object> runOne(String algo, JsspProblem problem, InstanceSpec spec) {
        IHeuristic heuristic = new MakespanEstimateHeuristic(problem);
        ScheduleValidator validator = new ScheduleValidator();

        SearchStatistics stats = "GREEDY".equals(algo)
                ? new SearchStatistics(1000, GREEDY_MAX_EXPANSIONS)
                : new SearchStatistics();

        TabuSearch[] tabuRef = new TabuSearch[1];
        ISearchAlgorithm solver = buildSolver(algo, problem, heuristic, stats, spec, tabuRef);

        PrintStream realOut = System.out;
        MemorySampler mem = new MemorySampler();
        mem.start();
        long start = System.currentTimeMillis();
        Schedule schedule;
        try {
            if ("TABU".equals(algo)) {
                System.setOut(new PrintStream(PrintStream.nullOutputStream()));
            }
            schedule = solver.solve(problem);
        } finally {
            System.setOut(realOut);
        }
        long elapsed = System.currentTimeMillis() - start;
        mem._stop();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("timeMs", elapsed);
        out.put("expanded", stats.getLastExpansions());
        out.put("reached", stats.getLastReached());
        out.put("maxDepth", stats.getLastMaxDepth());
        out.put("peakHeap", mem.getPeak());
        if (tabuRef[0] != null) {
            out.put("iterations", tabuRef[0].getHistory());
        }

        if (schedule == null) {
            out.put("valid", false);
            out.put("makespan", null);
            out.put("schedule", null);
            System.out.println("  " + algo + ": no solution in Limit  (" + elapsed + " ms)");
            return out;
        }
        ValidationResult vr = validator.validateSchedule(problem, schedule);
        int makespan = ScheduleValidator.makespan(schedule);
        out.put("valid", vr.valid());
        out.put("makespan", makespan);
        out.put("schedule", schedule.operations());
        System.out.println("  " + algo + ": makespan=" + makespan + " valid=" + vr.valid() + " time=" + elapsed + "ms expanded=" + stats.getLastExpansions());
        return out;
    }

    private static ISearchAlgorithm buildSolver(String algo, JsspProblem problem, IHeuristic heuristic, SearchStatistics stats, InstanceSpec spec, TabuSearch[] tabuRef) {
        switch (algo) {
            case "GREEDY":
                return new GreedySearch(heuristic, stats);
            case "BEAM":
                return new BeamSearch(heuristic, BEAM_WIDTH, stats);
            case "TABU":
                ScheduleEvaluator evaluator = new ScheduleEvaluator(problem);
                INeighbourhood neighbourhood = new N5Neighbourhood();
                IStartDecoder decoder = new RandomStartDecoder(new Random(42));
                TabuSearch tabuSearch = new TabuSearch(evaluator, neighbourhood, TABU_TENURE, spec.tabuNoImprove(), decoder);
                tabuRef[0] = tabuSearch;
                return tabuSearch;
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algo);
        }
    }

    private static void appendResult(StringBuilder json, String algo, Map<String, Object> res) {
        json.append(" \"").append(algo).append("\": {");
        json.append("\"timeMs\":").append(res.get("timeMs"));
        json.append(",\"expanded\":").append(res.get("expanded"));
        json.append(",\"reached\":").append(res.get("reached"));
        json.append(",\"maxDepth\":").append(res.get("maxDepth"));
        json.append(",\"peakHeap\":").append(res.get("peakHeap"));
        appendIterations(json, res.get("iterations"));
        json.append(",\"valid\":").append(res.get("valid"));
        json.append(",\"makespan\":").append(res.get("makespan") == null ? "null" : res.get("makespan"));
        json.append(",\"schedule\":");
        Object sched = res.get("schedule");
        if (sched == null) {
            json.append("null");
        } else {
            List<ScheduledOperation> ops = (List<ScheduledOperation>) sched;
            json.append("[");
            for (int i = 0; i < ops.size(); i++) {
                ScheduledOperation op = ops.get(i);
                json.append("{\"j\":").append(op.jobId())
                        .append(",\"m\":").append(op.machineId())
                        .append(",\"s\":").append(op.startTime())
                        .append(",\"e\":").append(op.endTime()).append("}");
                if (i < ops.size() - 1) json.append(",");
            }
            json.append("]");
        }
        json.append("}");
    }

    @SuppressWarnings("unchecked")
    private static void appendIterations(StringBuilder json, Object iterationsObj) {
        if (iterationsObj == null) {
            return;
        }
        List<TabuSearch.IterationSnapshot> iterations = (List<TabuSearch.IterationSnapshot>) iterationsObj;
        json.append(",\"iterations\":[");
        for (int i = 0; i < iterations.size(); i++) {
            TabuSearch.IterationSnapshot snap = iterations.get(i);
            json.append("[").append(snap.iteration()).append(",").append(snap.makespan()).append(",").append(snap.bestMakespan()).append("]");
            if (i < iterations.size() - 1) json.append(",");
        }
        json.append("]");
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }
}