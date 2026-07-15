package at.fhv;

import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.experiment.RunLog;
import at.fhv.experiment.RunLogWriter;
import at.fhv.model.exception.SolveFailedException;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.impl.BeamSearch;
import at.fhv.solver.impl.GreedySearch;
import at.fhv.solver.impl.TabuSearch;
import at.fhv.solver.impl.decoder.DwellStartDecoder;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;
import at.fhv.solver.impl.tabu.impl.DwellRepairNeighbourhood;
import at.fhv.solver.impl.tabu.impl.N5Neighbourhood;
import at.fhv.solver.validation.MemorySampler;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import at.fhv.visualization.RenderCharts;
import at.fhv.visualization.SearchStatistics;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

//.\gradlew.bat run --args="<command> [options]
//.\gradlew.bat run --args="run --algo TABU --instance benchmarks/demoanlage.txt --seed 1-20"
//.\gradlew.bat run --args="run --algo BEAM --instance benchmarks/demoanlage.txt --beam 1,5,20,50,100"
//.\gradlew.bat run --args="run --algo GREEDY,BEAM,TABU --instance benchmarks/ft06.txt,benchmarks/demoanlage.txt"

public class Main {
    private static final long GREEDY_MAX_EXPANSIONS = 100_000_000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void main(String[] args) throws IOException {
        String command = args.length > 0 ? args[0].toLowerCase() : "help";
        String[] rest = args.length > 0 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (command) {
            case "run":
                runAll(rest);
                break;
            case "charts":
                RenderCharts.run(rest);
                break;
            default:
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Commands:");
        System.out.println("  run  [--algo GREEDY|BEAM|TABU] [--instance <path>] [--beam <k>]");
        System.out.println("  [--tenure <n>] [--noimp <n>] [--seed <n>] [--out <folder>]");
        System.out.println("  Lists (1,5,20) and ranges (1-20) are allowed.");
        System.out.println("  charts  PNG charts from docs/assets/**/*-samples.csv");
        System.out.println();
        System.out.println("Examples:");
        System.out.println(" run --algo TABU --instance benchmarks/ft06.txt --seed 1-20");
        System.out.println("  run --algo BEAM --instance benchmarks/ft06.txt --beam 1,5,20,50,100");
        System.out.println("  run --algo GREEDY,BEAM,TABU --instance benchmarks/ft06.txt,benchmarks/la02.txt");
    }

    private static void runAll(String[] args) throws IOException {
        List<String> instances = options(args, "--instance", "benchmarks/demoanlage.txt");
        List<String> algorithms = options(args, "--algo", "GREEDY,BEAM,TABU");
        List<String> beamWidths = options(args, "--beam", "20");
        List<String> tenures = options(args, "--tenure", "10");
        List<String> noImprovements = options(args, "--noimp", "400");
        List<String> seeds = options(args, "--seed", "42");
        RunLogWriter writer = new RunLogWriter(Path.of(option(args, "--out", "runs")));

        Parser parser = new Parser();
        int written = 0;
        List<String> failures = new ArrayList<>();

        for (String instance : instances) {
            JsspProblem problem = parser.parse(instance);
            System.out.println("== " + instance + " (" + problem.getJobs().size() + " jobs, " + problem.getMachines().size() + " machines) ==");

            for (String algorithm : algorithms) {
                for (Map<String, Object> params : parameterSets(algorithm, beamWidths, tenures, noImprovements, seeds)) {
                    RunLog log = solveOnce(instance, problem, algorithm.toUpperCase(), params);
                    writer.write(log);
                    written++;
                    System.out.println("  " + log.runId() + "  makespan=" + (log.makespan() == null ? "-" : log.makespan()) + " valid=" + log.valid() + " time=" + log.timeMs() + "ms");

                    if (log.makespan() == null) {
                        failures.add(log.runId() + ": no schedule found (search gave up / dead-ended before reaching a goal)");
                    } else if (!log.valid()) {
                        failures.add(log.runId() + ": schedule is INVALID -> " + log.violations());
                    }
                }
            }
        }
        System.out.println(written + " log(s) written. Next: python3 tools/build_report.py");

        if (!failures.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append(failures.size()).append(" of ").append(written).append(" run(s) failed to produce a valid schedule:\n");
            for (String failure : failures) {
                message.append("  - ").append(failure).append("\n");
            }
            throw new SolveFailedException(message.toString());
        }
    }

    private static List<Map<String, Object>> parameterSets(String algorithm, List<String> beamWidths, List<String> tenures, List<String> noImprovements, List<String> seeds) {
        List<Map<String, Object>> sets = new ArrayList<>();
        String algo = algorithm.toUpperCase();

        if (algo.equals("GREEDY")) {
            sets.add(new LinkedHashMap<>());
            return sets;
        }
        if (algo.equals("BEAM")) {
            for (String beam : beamWidths) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("beam", Integer.parseInt(beam));
                sets.add(params);
            }
            return sets;
        }
        if (algo.equals("TABU")) {
            for (String tenure : tenures) {
                for (String noImprove : noImprovements) {
                    for (String seed : seeds) {
                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("tenure", Integer.parseInt(tenure));
                        params.put("noImprove", Integer.parseInt(noImprove));
                        params.put("seed", Long.parseLong(seed));
                        sets.add(params);
                    }
                }
            }
            return sets;
        }
        throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
    }

    private static RunLog solveOnce(String instance, JsspProblem problem, String algorithm, Map<String, Object> params) {
        IHeuristic heuristic = new MakespanEstimateHeuristic(problem);
        ScheduleValidator validator = new ScheduleValidator();

        SearchStatistics statistics = algorithm.equals("GREEDY")
                ? new SearchStatistics(1000, GREEDY_MAX_EXPANSIONS)
                : new SearchStatistics();

        TabuSearch[] tabuRef = new TabuSearch[1];
        ISearchAlgorithm solver = buildSolver(algorithm, problem, heuristic, statistics, params, tabuRef);

        PrintStream realOut = System.out;
        MemorySampler memorySampler = new MemorySampler();
        memorySampler.start();
        long start = System.currentTimeMillis();
        Schedule schedule;
        try {
            if (algorithm.equals("TABU")) { System.setOut(new PrintStream(PrintStream.nullOutputStream()));}
            schedule = solver.solve(problem);
        } finally {
            System.setOut(realOut);
        }
        long elapsed = System.currentTimeMillis() - start;
        memorySampler._stop();

        Integer makespan = null;
        boolean valid = false;
        List<String> violations = new ArrayList<>();
        if (schedule != null) {
            ValidationResult result = validator.validateSchedule(problem, schedule);
            makespan = ScheduleValidator.makespan(schedule);
            valid = result.valid();
            violations = result.violations();
        }

        return new RunLog(runId(instance, algorithm, params), LocalDateTime.now().toString(), instance, instanceKey(instance),
                algorithm, params, problem.getJobs().size(), problem.getMachines().size(), makespan,
                valid, elapsed, memorySampler.getPeak(), statistics.getLastExpansions(),
                statistics.getLastReached(), statistics.getLastMaxDepth(), tabuRef[0] == null ? null : tabuRef[0].getHistory(),
                schedule == null ? null : schedule.operations(), violations
        );
    }

    private static ISearchAlgorithm buildSolver(String algorithm, JsspProblem problem, IHeuristic heuristic, SearchStatistics statistics, Map<String, Object> params, TabuSearch[] tabuRef) {
        switch (algorithm) {
            case "GREEDY":
                return new GreedySearch(heuristic, statistics);
            case "BEAM":
                return new BeamSearch(heuristic, (Integer) params.get("beam"), statistics);
            case "TABU":
                long seed = (Long) params.get("seed");
                ScheduleEvaluator evaluator = new ScheduleEvaluator(problem);
                INeighbourhood neighbourhood = new DwellRepairNeighbourhood(new N5Neighbourhood(), problem);
                IStartDecoder decoder = new DwellStartDecoder(new Random(seed));

                TabuSearch tabuSearch = new TabuSearch(
                                evaluator,
                                neighbourhood,
                                (Integer) params.get("tenure"),
                                (Integer) params.get("noImprove"),
                                decoder,
                                seed
                        ).withVerbose(false);
                tabuRef[0] = tabuSearch;
                return tabuSearch;
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }

    private static String runId(String instance, String algorithm, Map<String, Object> params) {
        StringBuilder id = new StringBuilder();
        id.append(LocalDateTime.now().format(STAMP));
        id.append("_").append(instanceKey(instance));
        id.append("_").append(algorithm);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            id.append("_").append(entry.getKey()).append(entry.getValue());
        }
        return id.toString();
    }

    private static String instanceKey(String instance) {
        String name = Path.of(instance).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String option(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) { return args[i + 1]; }
        }
        return fallback;
    }

    private static List<String> options(String[] args, String name, String fallback) {
        List<String> values = new ArrayList<>();
        for (String part : option(args, name, fallback).split(",")) {
            String token = part.strip();
            if (token.isEmpty()) {
                continue;
            }
            if (isRange(token)) {
                int dash = token.indexOf('-');
                int from = Integer.parseInt(token.substring(0, dash));
                int to = Integer.parseInt(token.substring(dash + 1));
                for (int value = from; value <= to; value++) {
                    values.add(String.valueOf(value));
                }
            } else {
                values.add(token);
            }
        }
        return values;
    }

    private static boolean isRange(String token) {
        int dash = token.indexOf('-');
        if (dash <= 0 || dash == token.length() - 1) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (i != dash && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }
}