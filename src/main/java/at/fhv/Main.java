package at.fhv;

import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.Slack;
import at.fhv.evaluation.implementation.*;
import at.fhv.experiment.RunLog;
import at.fhv.experiment.RunLogWriter;
import at.fhv.model.exception.AppException;
import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.evaluation.implementation.FjsspMakespanHeuristic;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.SchedulingProblem;
import at.fhv.solver.validation.FjsspValidator;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.impl.beam.BeamSearch;
import at.fhv.solver.impl.beamStack.BeamStackSearch;
import at.fhv.solver.impl.bulb.BULBSearch;
import at.fhv.solver.impl.decoder.DwellStartDecoder;
import at.fhv.solver.impl.decoder.StartDecoder;
import at.fhv.solver.impl.greedy.GreedySearch;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;
import at.fhv.solver.impl.tabu.TabuSearch;
import at.fhv.solver.impl.tabu.impl.*;
import at.fhv.solver.validation.FjsspValidator;
import at.fhv.solver.validation.MemorySampler;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import at.fhv.stats.SearchStatistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

//   --instance benchmarks/demoanlage.txt
//   --mode JSSP|FJSSP           FJSSP: volle Zeit + Bad-Zuweisung, nur GREEDY|BEAM
//   --algo GREEDY|BEAM|BULB|BEAMSTACK|TABU
//   --heuristic MAKESPAN|NEXT-T-START|NEXT-T-MAX|COMBINED
//   --beam <k>
//   --start COLD|WARM           für Tabu
//   --nb AUTO|N5|N6|STRIDE
//   --tenure <n>
//   --noimp <n>
//   --time <ms>
//   --kick <n>
//   --seed <n>
//   --out <folder>

public class Main {
    // Safety caps so search can not run forever
    private static final long GREEDY_MAX_EXPANSIONS = 1_000_000;
    private static final int GREEDY_MAX_NODES = 1_500_000;
    private static final long BACKTRACKING_MAX_EXPANSIONS = 2_000_000;
    private static final int WARM_START_BEAM_WIDTH = 100; // when --start WARM
    private static final int TABU_MAX_RESTARTS = 100;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void main(String[] args) {
        try {
            run(args);
        } catch (AppException exception) {
            System.err.println("[" + exception.getErrorCode() + "] " + exception.getMessage());
            System.exit(1);
        } catch (IOException exception) {
            System.err.println("[IO_ERROR] " + exception.getMessage());
            System.exit(2);
        }
    }

    private static void run(String[] args) throws IOException {
        List<String> instances = options(args, "--instance", "benchmarks/demoanlage.txt");
        String mode = option(args, "--mode", "JSSP").toUpperCase();
        List<String> algorithms = options(args, "--algo", defaultAlgorithms(mode));
        Path outFolder = Path.of(option(args, "--out", "runs"));

        RunLogWriter writer = new RunLogWriter(outFolder);
        Parser parser = new Parser();

        for (String instance : instances) {
            Loaded loaded = load(parser, instance, mode);
            for (String Algorithm : algorithms) {
                String algorithm = Algorithm.toUpperCase().replace("-", "");
                if (mode.equals("FJSSP") && !algorithm.equals("GREEDY") && !algorithm.equals("BEAM")) {
                    System.err.println("[skip] " + algorithm + " is not available in FJSSP mode (use GREEDY or BEAM)");
                    continue;
                }
                for (RunConfig config : configsFor(algorithm, args)) {
                    writer.write(solveOnce(instance, loaded, mode, algorithm, config, outFolder));
                }
            }
        }
    }

    private static String defaultAlgorithms(String mode) {
        return mode.equals("FJSSP") ? "GREEDY,BEAM" : "GREEDY,BEAM,TABU";
    }

    private record Loaded(SchedulingProblem problem, int jobCount, int machineCount) {}

    private static Loaded load(Parser parser, String instance, String mode) throws IOException {
        if (mode.equals("FJSSP")) {
            FjsspProblem fjssp = parser.parseFjssp(instance);
            return new Loaded(fjssp, fjssp.getJobs().size(), fjssp.getMachines().size());
        }
        JsspProblem jssp = parser.parse(instance);
        return new Loaded(jssp, jssp.getJobs().size(), jssp.getMachines().size());
    }

    private interface RunConfig {
        String heuristic();
        Map<String, Object> toParams();
    }

    private record GreedyConfig(String heuristic) implements RunConfig {
        @Override
        public Map<String, Object> toParams() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("heuristic", heuristic);
            return params;
        }
    }

    // For Beam, BULB, Beamstack - they only differ in solver class
    private record BeamConfig(String heuristic, int beamWidth) implements RunConfig {
        @Override
        public Map<String, Object> toParams() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("heuristic", heuristic);
            params.put("beam", beamWidth);
            return params;
        }
    }

    private record TabuConfig(String heuristic, String start, String neighbourhood, int tenure, int noImprove, long timeMs, int kick, long seed) implements RunConfig {
        @Override
        public Map<String, Object> toParams() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("heuristic", heuristic);
            params.put("start", start);
            params.put("nb", neighbourhood);
            params.put("tenure", tenure);
            params.put("noImprove", noImprove);
            if (timeMs > 0) { params.put("timeMs", timeMs); }
            params.put("kick", kick);
            params.put("seed", seed);
            return params;
        }
    }

    private static List<RunConfig> configsFor(String algorithm, String[] args) {
        switch (algorithm) {
            case "GREEDY": return greedyConfigs(args);
            case "BEAM": case "BULB": case "BEAMSTACK": return beamConfigs(args);
            case "TABU": return tabuConfigs(args);
            default: throw new IllegalArgumentException("Unknown algorithm: "+algorithm);
        }
    }

    private static List<RunConfig> greedyConfigs(String[] args) {
        List<RunConfig> configs = new ArrayList<>();
        for (String heuristic : options(args, "--heuristic", "MAKESPAN")) {
            configs.add(new GreedyConfig(heuristic.toUpperCase()));
        }
        return configs;
    }

    private static List<RunConfig> beamConfigs(String[] args) {
        List<RunConfig> configs = new ArrayList<>();
        for (String heuristic : options(args, "--heuristic", "MAKESPAN")) {
            for (String beam : options(args, "--beam", "20")) {
                configs.add(new BeamConfig(heuristic.toUpperCase(), Integer.parseInt(beam)));
            }
        }
        return configs;
    }

    // combinations() does cross product bc we want every combination of the values
    private static List<RunConfig> tabuConfigs(String[] args) {
        List<Dimension> dimensions = List.of(
                new Dimension("heuristic", options(args, "--heuristic", "MAKESPAN")),
                new Dimension("start", options(args, "--start", "COLD")),
                new Dimension("nb", options(args, "--nb", "AUTO")),
                new Dimension("tenure", options(args, "--tenure", "10")),
                new Dimension("noimp", options(args, "--noimp", "300")),
                new Dimension("time", options(args, "--time", "0")),
                new Dimension("kick", options(args, "--kick", "2")),
                new Dimension("seed", options(args, "--seed", "42"))
        );

        List<RunConfig> configs = new ArrayList<>();
        for (Map<String, String> combination : combinations(dimensions)) {
            configs.add(new TabuConfig(
                    combination.get("heuristic").toUpperCase(),
                    combination.get("start").toUpperCase(),
                    combination.get("nb").toUpperCase(),
                    Integer.parseInt(combination.get("tenure")),
                    Integer.parseInt(combination.get("noimp")),
                    Long.parseLong(combination.get("time")),
                    Integer.parseInt(combination.get("kick")),
                    Long.parseLong(combination.get("seed"))
            ));
        }
        return configs;
    }

    private record Dimension(String key, List<String> values) {} // 1 parameter & all its values

    private static List<Map<String, String>> combinations(List<Dimension> dimensions) {
        List<Map<String, String>> result = new ArrayList<>();
        result.add(new LinkedHashMap<>());

        for (Dimension dimension : dimensions) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> partial : result) {
                for (String value : dimension.values()) {
                    Map<String, String> combination = new LinkedHashMap<>(partial);
                    combination.put(dimension.key(), value);
                    next.add(combination);
                }
            }
            result = next;
        }
        return result;
    }

    private static RunLog solveOnce(String instance, Loaded loaded, String mode, String algorithm, RunConfig config, Path outFolder) throws IOException {
        SchedulingProblem problem = loaded.problem();
        Map<String, Object> params = config.toParams();
        IHeuristic heuristic = buildHeuristic(config.heuristic(), problem);
        SearchStatistics statistics = buildStatistics(algorithm);
        String runId = runId(instance, mode, algorithm, params);
        Files.createDirectories(outFolder);
        statistics.enableLog(outFolder.resolve(runId + "-samples.csv").toString());
        ISearchAlgorithm solver = buildSolver(algorithm, problem, heuristic, statistics, config);

        MemorySampler memorySampler = new MemorySampler();
        memorySampler.start();
        long start = System.currentTimeMillis();
        Schedule schedule = solver.solve(problem);
        long elapsed = System.currentTimeMillis() - start;
        memorySampler.shutdown();
        statistics.closeLog();

        List<TabuSearch.IterationSnapshot> history = (solver instanceof TabuSearch tabu) ? tabu.getHistory() : null;

        Integer makespan = null;
        boolean valid = false;
        List<String> violations = new ArrayList<>();
        if (schedule != null) {
            ValidationResult result = validate(problem, schedule);
            makespan = computeMakespan(schedule);
            valid = result.valid();
            violations = result.violations();
        }

        return new RunLog(runId, LocalDateTime.now().toString(), instance, instanceKey(instance), algorithm, mode, params, loaded.jobCount(), loaded.machineCount(), makespan,
                valid, elapsed, memorySampler.getPeak(), statistics.getLastExpansions(), statistics.getLastReached(), statistics.getLastMaxDepth(), history,
                schedule == null ? null : schedule.operations(), violations
        );
    }

    private static SearchStatistics buildStatistics(String algorithm) {
        if (algorithm.equals("GREEDY")) { return new SearchStatistics(1000, GREEDY_MAX_EXPANSIONS);}
        if (algorithm.equals("BULB") || algorithm.equals("BEAMSTACK")) { return new SearchStatistics(1000, BACKTRACKING_MAX_EXPANSIONS);}
        return new SearchStatistics();
    }

    private static ValidationResult validate(SchedulingProblem problem, Schedule schedule) {
        if (problem instanceof FjsspProblem fjssp) {
            return new FjsspValidator().validateSchedule(fjssp, schedule);
        }
        return new ScheduleValidator().validateSchedule((JsspProblem) problem, schedule);
    }

    private static IHeuristic buildHeuristic(String name, SchedulingProblem problem) {
        if (problem instanceof FjsspProblem fjssp) {
            return new FjsspMakespanHeuristic(fjssp);
        }
        JsspProblem jsspProblem = (JsspProblem) problem;
        String selected = (name == null) ? "MAKESPAN" : name.toUpperCase();
        switch (selected) {
            case "MAKESPAN": return new MakespanEstimateHeuristic(jsspProblem);
            case "NEXT-T-START": return new NextTStartHeuristic(jsspProblem);
            case "NEXT-T-MAX": return new NextTMaxHeuristic(jsspProblem, new Slack(jsspProblem));
            case "COMBINED": Slack slack = new Slack(jsspProblem);
                return new CombineHeuristics(new MakespanEstimateHeuristic(jsspProblem), List.of(new NextTMaxHeuristic(jsspProblem, slack), new NextTStartHeuristic(jsspProblem)));
            default: throw new IllegalArgumentException("Unknown heuristic: " + name);
        }
    }

    private static ISearchAlgorithm buildSolver(String algorithm, SchedulingProblem problem, IHeuristic heuristic, SearchStatistics statistics, RunConfig config) {
        return switch (config) {
            case GreedyConfig greedy -> new GreedySearch(heuristic, statistics, GREEDY_MAX_NODES);
            case BeamConfig beam -> buildBeamFamily(algorithm, heuristic, beam.beamWidth(), statistics);
            case TabuConfig tabu -> buildTabuSearch((JsspProblem) problem, heuristic, tabu);
            default -> throw new IllegalArgumentException("Unknown config type: " + config.getClass().getName());
        };
    }

    private static ISearchAlgorithm buildBeamFamily(String algorithm, IHeuristic heuristic, int beamWidth, SearchStatistics statistics) {
        switch (algorithm) {
            case "BEAM": return new BeamSearch(heuristic, beamWidth, statistics);
            case "BULB": return new BULBSearch(heuristic, beamWidth, statistics);
            case "BEAMSTACK": return new BeamStackSearch(heuristic, beamWidth, statistics);
            default: throw new IllegalArgumentException("Unknown beam algorithm: " + algorithm);
        }
    }

    private static TabuSearch buildTabuSearch(JsspProblem problem, IHeuristic heuristic, TabuConfig config) {
        ScheduleEvaluator evaluator = new ScheduleEvaluator(problem);
        INeighbourhood neighbourhood = buildNeighbourhood(config.neighbourhood(), problem);
        IStartDecoder decoder = buildStartDecoder(config.start(), problem, heuristic, config.seed());

        return new TabuSearch(evaluator, neighbourhood, config.tenure(), config.noImprove(), decoder,
                config.seed(), TABU_MAX_RESTARTS, config.timeMs(), config.kick()).withVerbose(false);
    }

    private static IStartDecoder buildStartDecoder(String name, JsspProblem problem, IHeuristic heuristic, long seed) {
        IStartDecoder cold = new DwellStartDecoder(new Random(seed));
        String selected = name == null ? "COLD" : name.toUpperCase();

        switch (selected) {
            case "COLD": return cold;
            case "WARM": return new StartDecoder(new BeamSearch(heuristic, WARM_START_BEAM_WIDTH, new SearchStatistics()), cold);
            default: throw new IllegalArgumentException("Unknown start: " + name);
        }
    }

    // Stride == Auto, da es als einzige Nachbarschaft Blocking und parallele Bäder berücksichtigt - Rest zum Vergleichen
    private static INeighbourhood buildNeighbourhood(String name, JsspProblem problem) {
        String selected = name == null ? "AUTO" : name.toUpperCase();
        if (selected.equals("AUTO")) { selected = problem.isBlocking() ? "STRIDE" : "N5";}

        switch (selected) {
            case "N5": return new N5Neighbourhood();
            case "N6": return new N6Neighbourhood();
            case "STRIDE": return new StrideNeighbourhood();
            default: throw new IllegalArgumentException("Unknown neighbourhood: " + name);
        }
    }


    private static String runId(String instance, String mode, String algorithm, Map<String, Object> params) {
        StringBuilder id = new StringBuilder();
        id.append(LocalDateTime.now().format(STAMP));
        id.append("_").append(instanceKey(instance));
        id.append("_").append(mode);
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

    private static String option(String[] args, String name, String fallback) { // reads single value
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) { return args[i + 1]; }
        }
        return fallback;
    }

    private static List<String> options(String[] args, String name, String fallback) { // reads value as list
        List<String> values = new ArrayList<>();
        for (String part : option(args, name, fallback).split(",")) {
            String token = part.strip();
            if (token.isEmpty()) { continue; }
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
        if (dash <= 0 || dash == token.length() - 1) { return false; }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (i != dash && (c < '0' || c > '9')) { return false; }
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