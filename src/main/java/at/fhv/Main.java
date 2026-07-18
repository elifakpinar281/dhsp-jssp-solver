package at.fhv;

import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.Slack;
import at.fhv.evaluation.implementation.CombineHeuristics;
import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.evaluation.implementation.NextTStartHeuristic;
import at.fhv.evaluation.implementation.NextTMaxHeuristic;
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
import at.fhv.solver.impl.decoder.StartDecoder;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;
import at.fhv.solver.impl.tabu.impl.*;
import at.fhv.solver.validation.MemorySampler;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import at.fhv.visualization.RenderCharts;
import at.fhv.visualization.SearchStatistics;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Main {
    private static final long GREEDY_MAX_EXPANSIONS = 1_000_000;
    private static final int GREEDY_MAX_NODES = 1_500_000;
    private static final int WARM_START_BEAM_WIDTH = 100;
    private static final int TABU_MAX_RESTARTS = 100;
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
        System.out.println("  [--tenure <n>] [--noimp <n>] [--seed <n>] [--nb AUTO|N1|N5|N6|ADJ|STRIDE]");
        System.out.println("  [--time <ms>] wall clock budget per TABU run, 0 = use --noimp only");
        System.out.println("  [--kick <n>] non improving iterations before perturbing, default 2");
        System.out.println("  [--start COLD|WARM] [--out <folder>]");
        System.out.println("  [--heuristic MAKESPAN|NEXT-T-START|NEXT-T-MAX|COMBINED] one or a list");
        System.out.println("  Lists (1,5,20) and ranges (1-20) are allowed.");
        System.out.println("  charts  PNG charts from docs/assets/**/*-samples.csv");
        System.out.println();
        System.out.println("Examples:");
        System.out.println(" run --algo TABU --instance benchmarks/ft06.txt --seed 1-20");
        System.out.println("  run --algo BEAM --instance benchmarks/ft06.txt --beam 1,5,20,50,100");
        System.out.println("  run --algo BEAM --instance benchmarks/demoanlage.txt --heuristic MAKESPAN,NEXT-T-START,NEXT-T-MAX,COMBINED");
    }

    private static void runAll(String[] args) throws IOException {
        List<String> instances = options(args, "--instance", "benchmarks/demoanlage.txt");
        List<String> algorithms = options(args, "--algo", "GREEDY,BEAM,TABU");
        List<String> beamWidths = options(args, "--beam", "20");
        List<String> tenures = options(args, "--tenure", "10");
        List<String> noImprovements = options(args, "--noimp", "300");
        List<String> seeds = options(args, "--seed", "42");
        List<String> neighbourhoods = options(args, "--nb", "AUTO");
        List<String> starts = options(args, "--start", "COLD");
        List<String> timeBudgets = options(args, "--time", "0");
        List<String> kicks = options(args, "--kick", "2");
        List<String> heuristics = options(args, "--heuristic", "MAKESPAN");
        Path outFolder = Path.of(option(args, "--out", "runs"));
        RunLogWriter writer = new RunLogWriter(outFolder);

        Parser parser = new Parser();
        int written = 0;
        List<String> failures = new ArrayList<>();

        for (String instance : instances) {
            JsspProblem problem = parser.parse(instance);
            System.out.println("== " + instance + " (" + problem.getJobs().size() + " jobs, " + problem.getMachines().size() + " machines) ==");

            for (String algorithm : algorithms) {
                for (Map<String, Object> params : parameterSets(algorithm, beamWidths, tenures, noImprovements, seeds, neighbourhoods, starts, timeBudgets, kicks, heuristics)) {
                    boolean[] stoppedByLimit = new boolean[1];
                    RunLog log = solveOnce(instance, problem, algorithm.toUpperCase(), params, outFolder, stoppedByLimit);
                    writer.write(log);
                    written++;
                    System.out.println("  " + log.runId() + "  makespan=" + (log.makespan() == null ? "-" : log.makespan()) + " valid=" + log.valid() + " time=" + log.timeMs() + "ms");

                    if (log.makespan() == null) {
                        failures.add(log.runId() + ": no schedule found (" + (stoppedByLimit[0] ? "gave up at the configured limit after " + log.expanded() + " expansions / " + log.reached() + " reached states" : "dead-ended before reaching a goal") + ")");
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

    private static List<Map<String, Object>> parameterSets(String algorithm, List<String> beamWidths, List<String> tenures, List<String> noImprovements, List<String> seeds, List<String> neighbourhoods, List<String> starts, List<String> timeBudgets, List<String> kicks, List<String> heuristics) {
        List<Map<String, Object>> sets = new ArrayList<>();
        String algo = algorithm.toUpperCase();

        for (String heuristic : heuristics) {
            String heur = heuristic.toUpperCase();

            if (algo.equals("GREEDY")) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("heur", heur);
                sets.add(params);
            } else if (algo.equals("BEAM")) {
                for (String beam : beamWidths) {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("heur", heur);
                    params.put("beam", Integer.parseInt(beam));
                    sets.add(params);
                }
            } else if (algo.equals("TABU")) {
                for (String start : starts) {
                    for (String neighbourhood : neighbourhoods) {
                        for (String tenure : tenures) {
                            for (String noImprove : noImprovements) {
                                for (String time : timeBudgets) {
                                    for (String kick : kicks) {
                                        for (String seed : seeds) {
                                            Map<String, Object> params = new LinkedHashMap<>();
                                            params.put("heur", heur);
                                            params.put("start", start.toUpperCase());
                                            params.put("nb", neighbourhood.toUpperCase());
                                            params.put("tenure", Integer.parseInt(tenure));
                                            params.put("noImprove", Integer.parseInt(noImprove));
                                            long timeBudget = Long.parseLong(time);
                                            if (timeBudget > 0) { params.put("timeMs", timeBudget); }
                                            params.put("kick", Integer.parseInt(kick));
                                            params.put("seed", Long.parseLong(seed));
                                            sets.add(params);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
            }
        }
        return sets;
    }

    private static RunLog solveOnce(String instance, JsspProblem problem, String algorithm, Map<String, Object> params, Path outFolder, boolean[] stoppedByLimit) throws IOException {
        IHeuristic heuristic = buildHeuristic((String) params.get("heur"), problem);
        ScheduleValidator validator = new ScheduleValidator();
        SearchStatistics statistics = algorithm.equals("GREEDY") ? new SearchStatistics(1000, GREEDY_MAX_EXPANSIONS) : new SearchStatistics();

        String runId = runId(instance, algorithm, params);
        Files.createDirectories(outFolder);
        statistics.enableLog(outFolder.resolve(runId + "-samples.csv").toString());

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
        statistics.closeLog();
        stoppedByLimit[0] = statistics.isStoppedByLimit();

        Integer makespan = null;
        boolean valid = false;
        List<String> violations = new ArrayList<>();
        if (schedule != null) {
            ValidationResult result = validator.validateSchedule(problem, schedule);
            makespan = computeMakespan(schedule);
            valid = result.valid();
            violations = result.violations();
        }

        return new RunLog(runId, LocalDateTime.now().toString(), instance, instanceKey(instance),
                algorithm, params, problem.getJobs().size(), problem.getMachines().size(), makespan,
                valid, elapsed, memorySampler.getPeak(), statistics.getLastExpansions(),
                statistics.getLastReached(), statistics.getLastMaxDepth(), tabuRef[0] == null ? null : tabuRef[0].getHistory(),
                schedule == null ? null : schedule.operations(), violations
        );
    }

    private static IHeuristic buildHeuristic(String name, JsspProblem problem) {
        String selected = (name == null) ? "MAKESPAN" : name.toUpperCase();
        switch (selected) {
            case "MAKESPAN":
                return new MakespanEstimateHeuristic(problem);
            case "NEXT-T-START":
                return new NextTStartHeuristic(problem);
            case "NEXT-T-MAX":
                return new NextTMaxHeuristic(problem, new Slack(problem));
            case "COMBINED":
                return new CombineHeuristics(problem);
            default:
                throw new IllegalArgumentException("Unknown heuristic: " + name);
        }
    }

    private static ISearchAlgorithm buildSolver(String algorithm, JsspProblem problem, IHeuristic heuristic, SearchStatistics statistics, Map<String, Object> params, TabuSearch[] tabuRef) {
        switch (algorithm) {
            case "GREEDY":
                return new GreedySearch(heuristic, statistics, GREEDY_MAX_NODES);
            case "BEAM":
                return new BeamSearch(heuristic, (Integer) params.get("beam"), statistics);
            case "TABU":
                long seed = (Long) params.get("seed");
                ScheduleEvaluator evaluator = new ScheduleEvaluator(problem);
                INeighbourhood neighbourhood = buildNeighbourhood((String) params.get("nb"), problem);
                IStartDecoder decoder = buildStartDecoder((String) params.get("start"), problem, heuristic, seed);

                Long timeBudget = (Long) params.get("timeMs");

                TabuSearch tabuSearch = new TabuSearch(
                        evaluator,
                        neighbourhood,
                        (Integer) params.get("tenure"),
                        (Integer) params.get("noImprove"),
                        decoder,
                        seed,
                        TABU_MAX_RESTARTS,
                        timeBudget == null ? 0L : timeBudget,
                        (Integer) params.get("kick")
                ).withVerbose(false);
                tabuRef[0] = tabuSearch;
                return tabuSearch;
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }

    private static IStartDecoder buildStartDecoder(String name, JsspProblem problem, IHeuristic heuristic, long seed) {
        IStartDecoder cold = new DwellStartDecoder(new Random(seed));
        String selected = name == null ? "COLD" : name.toUpperCase();

        switch (selected) {
            case "COLD":
                return cold;
            case "WARM":
                return new StartDecoder(new BeamSearch(heuristic, WARM_START_BEAM_WIDTH, new SearchStatistics()), cold);
            default:
                throw new IllegalArgumentException("Unknown start: " + name);
        }
    }

    private static INeighbourhood buildNeighbourhood(String name, JsspProblem problem) {
        String selected = name == null ? "AUTO" : name.toUpperCase();
        if (selected.equals("AUTO")) {
            selected = problem.isBlocking() ? "STRIDE" : "N5";
        }

        switch (selected) {
            case "N1":
                return new N1Neighbourhood();
            case "N5":
                return new N5Neighbourhood();
            case "N6":
                return new N6Neighbourhood();
            case "ADJ":
                return new AdjacentSwapNeighbourhood();
            case "STRIDE":
                return new StrideNeighbourhood();
            default:
                throw new IllegalArgumentException("Unknown neighbourhood: " + name);
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

    private static int computeMakespan(Schedule schedule) {
        int makespan = 0;
        for (var operation : schedule.operations()) {
            makespan = Math.max(makespan, operation.endTime());
        }
        return makespan;
    }
}
