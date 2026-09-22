package at.fhv.experiment;

import at.fhv.Parser;
import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.Slack;
import at.fhv.evaluation.implementation.CombineHeuristics;
import at.fhv.evaluation.implementation.CountingHeuristic;
import at.fhv.evaluation.implementation.fjssp.FjsspMakespanHeuristic;
import at.fhv.evaluation.implementation.fjssp.FjsspNextTMaxHeuristic;
import at.fhv.evaluation.implementation.fjssp.FjsspNextTStartHeuristic;
import at.fhv.evaluation.implementation.fjssp.FjsspSlack;
import at.fhv.evaluation.implementation.jssp.AdmissibleBoundHeuristic;
import at.fhv.evaluation.implementation.jssp.FutureMakespanHeuristic;
import at.fhv.evaluation.implementation.jssp.MakespanEstimateHeuristic;
import at.fhv.evaluation.implementation.jssp.NextTMaxHeuristic;
import at.fhv.evaluation.implementation.jssp.NextTStartHeuristic;
import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.model.jssp.Transition;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.SchedulingProblem;
import at.fhv.solver.State;
import at.fhv.solver.impl.astar.AStarSearch;
import at.fhv.solver.impl.beam.BeamSearch;
import at.fhv.solver.impl.beamStack.BeamStackSearch;
import at.fhv.solver.impl.bulb.BULBSearch;
import at.fhv.solver.impl.decoder.DwellStartDecoder;
import at.fhv.solver.impl.greedy.GreedySearch;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;
import at.fhv.solver.impl.tabu.TabuSearch;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.impl.SegmentSwapNeighbourhood;
import at.fhv.solver.impl.tabu.impl.StrideNeighbourhood;
import at.fhv.stats.SearchStatistics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RegressionCheck {
    private static final Path BASELINE = Path.of("regression", "baseline.csv");
    private static final String SMALL = "benchmarks/demoanlage_25.txt";
    private static final String MEDIUM = "benchmarks/demoanlage_50.txt";

    private record Result(String name, String value, long millis) {}

    public static void main(String[] args) throws IOException {
        boolean reset = args.length > 0 && args[0].equals("--reset");
        Parser parser = new Parser();
        JsspProblem small = parser.parse(SMALL);
        JsspProblem medium = parser.parse(MEDIUM);
        FjsspProblem smallFjssp = parser.parseFjssp(SMALL);

        List<Result> results = new ArrayList<>();

        results.add(heuristicChecksum("heuristic MAKESPAN", small, new MakespanEstimateHeuristic(small)));
        results.add(heuristicChecksum("heuristic REMAINING", small, new FutureMakespanHeuristic(small)));
        results.add(heuristicChecksum("heuristic NEXT-T-START", small, new NextTStartHeuristic(small)));
        results.add(heuristicChecksum("heuristic NEXT-T-MAX", small, new NextTMaxHeuristic(small, new Slack(small))));
        Slack slack = new Slack(small);
        results.add(heuristicChecksum("heuristic COMBINED", small, new CombineHeuristics(new MakespanEstimateHeuristic(small), List.of(new NextTMaxHeuristic(small, slack), new NextTStartHeuristic(small)))));
        results.add(heuristicChecksum("heuristic ADMISSIBLE", small, new AdmissibleBoundHeuristic(small)));
        results.add(heuristicChecksum("heuristic FJSSP MAKESPAN", smallFjssp, new FjsspMakespanHeuristic(smallFjssp)));
        results.add(heuristicChecksum("heuristic FJSSP NEXT-T-START", smallFjssp, new FjsspNextTStartHeuristic(smallFjssp)));
        results.add(heuristicChecksum("heuristic FJSSP NEXT-T-MAX", smallFjssp, new FjsspNextTMaxHeuristic(smallFjssp, new FjsspSlack(smallFjssp))));
        results.add(runSearch("BEAM MAKESPAN w20 n25", small, new MakespanEstimateHeuristic(small), "BEAM", 20));
        results.add(runSearch("BEAM MAKESPAN w100 n50", medium, new MakespanEstimateHeuristic(medium), "BEAM", 100));
        results.add(runSearch("BEAM NEXT-T-START w20 n25", small, new NextTStartHeuristic(small), "BEAM", 20));
        results.add(runSearch("BULB MAKESPAN w20 n25", small, new MakespanEstimateHeuristic(small), "BULB", 20));
        results.add(runSearch("BEAMSTACK MAKESPAN w20 n25 (3 sweeps)", small, new MakespanEstimateHeuristic(small), "BEAMSTACK", 20));
        results.add(runSearch("BEAMSTACK ADMISSIBLE w20 n25 (3 sweeps)", small, new AdmissibleBoundHeuristic(small), "BEAMSTACK", 20));
        results.add(runSearch("GREEDY MAKESPAN n25 (200k nodes)", small, new MakespanEstimateHeuristic(small), "GREEDY", 0));
        results.add(runSearch("ASTAR REMAINING n25 (200k nodes)", small, new FutureMakespanHeuristic(small), "ASTAR", 0));
        results.add(runSearch("FJSSP BEAM MAKESPAN w20 n25", smallFjssp, new FjsspMakespanHeuristic(smallFjssp), "BEAM", 20));
        results.add(runTabu("TABU STRIDE n25 (2 restarts)", small, new StrideNeighbourhood()));
        results.add(runTabu("TABU SEGMENT n25 (2 restarts)", small, new SegmentSwapNeighbourhood(small)));

        printAndCompare(results, reset);
    }

    private static Result heuristicChecksum(String name, SchedulingProblem problem, IHeuristic heuristic) {
        long start = System.nanoTime();
        Random random = new Random(1);
        double sum = 0.0;
        int count = 0;

        for (int path = 0; path < 20; path++) {
            State state = problem.createInitialState();
            while (!problem.isGoal(state)) {
                sum = sum + heuristic.evaluate(state);
                count++;
                List<Transition> transitions = problem.expand(state);
                if (transitions.isEmpty()) {
                    break;
                }
                state = transitions.get(random.nextInt(transitions.size())).state();
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        return new Result(name, "states=" + count + " sum=" + sum, millis);
    }

    private static Result runSearch(String name, SchedulingProblem problem, IHeuristic heuristic, String algorithm, int beamWidth) {
        CountingHeuristic counting = new CountingHeuristic(heuristic);
        SearchStatistics statistics = new SearchStatistics(1000, 0);
        ISearchAlgorithm solver;

        switch (algorithm) {
            case "BEAM": solver = new BeamSearch(counting, beamWidth, statistics); break;
            case "BULB": solver = new BULBSearch(counting, beamWidth, statistics); break;
            case "BEAMSTACK": solver = new BeamStackSearch(counting, beamWidth, statistics, 3); break;
            case "GREEDY": solver = new GreedySearch(counting, statistics, 200_000); break;
            case "ASTAR": solver = new AStarSearch(counting, statistics, 200_000, 1.0); break;
            default: throw new IllegalArgumentException("Unknown algorithm " + algorithm);
        }

        long start = System.nanoTime();
        Schedule schedule = solver.solve(problem);
        long millis = (System.nanoTime() - start) / 1_000_000;

        String value = "makespan=" + makespanOf(schedule)
                + " expanded=" + statistics.getLastExpansions()
                + " evaluations=" + counting.getCount();
        return new Result(name, value, millis);
    }

    private static Result runTabu(String name, JsspProblem problem, INeighbourhood neighbourhood) {
        SearchStatistics statistics = new SearchStatistics(1000, 0);
        TabuSearch tabu = new TabuSearch(new ScheduleEvaluator(problem), neighbourhood, 10, 2,
                new DwellStartDecoder(new Random(42)), 42, 2, 0L, 2, statistics);

        long start = System.nanoTime();
        Schedule schedule = tabu.solve(problem);
        long millis = (System.nanoTime() - start) / 1_000_000;

        String value = "makespan=" + makespanOf(schedule) + " steps=" + tabu.getHistory().size();
        return new Result(name, value, millis);
    }

    private static String makespanOf(Schedule schedule) {
        if (schedule == null) {
            return "none";
        }
        int makespan = 0;
        for (ScheduledOperation operation : schedule.operations()) {
            makespan = Math.max(makespan, operation.endTime());
        }
        return String.valueOf(makespan);
    }

    private static void printAndCompare(List<Result> results, boolean reset) throws IOException {
        if (reset || !Files.exists(BASELINE)) {
            writeBaseline(results);
            System.out.println();
            System.out.println("Baseline written to " + BASELINE.toAbsolutePath());
            for (Result result : results) {
                System.out.printf("  %-40s %8d ms   %s%n", result.name(), result.millis(), result.value());
            }
            return;
        }

        Map<String, String> baseline = readBaseline();
        int differences = 0;
        System.out.println();
        for (Result result : results) {
            String expected = baseline.get(result.name());
            String status;
            if (expected == null) {
                status = "NEW  ";
            } else if (expected.equals(result.value())) {
                status = "OK   ";
            } else {
                status = "DIFF ";
                differences++;
            }
            System.out.printf("%s %-40s %8d ms   %s%n", status, result.name(), result.millis(), result.value());
            if (status.equals("DIFF ")) {
                System.out.printf("      %-40s             expected: %s%n", "", expected);
            }
        }
        System.out.println();
        if (differences == 0) {
            System.out.println("Everything matches the baseline.");
        } else {
            System.out.println(differences + " differences. For a performance step this is a bug.");
            System.out.println("If the step changes behaviour on purpose: ./gradlew regression --args=\"--reset\"");
        }
    }

    private static void writeBaseline(List<Result> results) throws IOException {
        Files.createDirectories(BASELINE.getParent());
        StringBuilder content = new StringBuilder();
        for (Result result : results) {
            content.append(result.name()).append(";").append(result.value()).append("\n");
        }
        Files.write(BASELINE, content.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> readBaseline() throws IOException {
        Map<String, String> baseline = new LinkedHashMap<>();
        for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
            int separator = line.indexOf(';');
            if (separator < 0) {
                continue;
            }
            baseline.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return baseline;
    }
}