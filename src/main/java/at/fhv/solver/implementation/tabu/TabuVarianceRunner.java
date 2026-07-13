package at.fhv;

import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.decoder.impl.RandomStartDecoder;
import at.fhv.solver.implementation.BeamSearch;
import at.fhv.solver.implementation.tabu.INeighbourhood;
import at.fhv.solver.implementation.tabu.ScheduleEvaluator;
import at.fhv.solver.implementation.tabu.TabuSearch;
import at.fhv.solver.implementation.tabu.impl.N5Neighbourhood;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import at.fhv.visualization.SearchStatistics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TabuVarianceRunner {
    private static final String INSTANCE = "benchmarks/demoanlage.txt";
    private static final int RUNS = 20;
    private static final long BASE_SEED = 1L;

    private static final int TABU_TENURE = 10;
    private static final int TABU_MAX_NO_IMPROVEMENT = 500;

    private static final int BEAM_WIDTH = 20;

    public static void main(String[] args) throws IOException {
        Parser parser = new Parser();
        JsspProblem problem = parser.parse(INSTANCE);
        IHeuristic heuristic = new MakespanEstimateHeuristic(problem);
        ScheduleValidator validator = new ScheduleValidator();

        System.out.println("Instance: " + INSTANCE);
        System.out.println();

        BeamSearch beamSearch = new BeamSearch(heuristic, BEAM_WIDTH, new SearchStatistics());
        long beamStart = System.currentTimeMillis();
        Schedule beamSchedule = beamSearch.solve(problem);
        long beamElapsed = System.currentTimeMillis() - beamStart;
        int beamMakespan = ScheduleValidator.makespan(beamSchedule);
        ValidationResult beamResult = validator.validateSchedule(problem, beamSchedule);
        System.out.println("Beam Search (k=" + BEAM_WIDTH + "): makespan=" + beamMakespan + ", timeMs=" + beamElapsed + ", valid=" + beamResult.valid());
        System.out.println();

        List<Integer> makespans = new ArrayList<>();
        List<Long> timesMs = new ArrayList<>();
        int invalidCount = 0;

        System.out.println("seed,makespan,timeMs,valid");
        for (int i = 0; i < RUNS; i++) {
            long seed = BASE_SEED + i;

            ScheduleEvaluator evaluator = new ScheduleEvaluator(problem);
            INeighbourhood neighbourhood = new N5Neighbourhood();
            IStartDecoder decoder = new RandomStartDecoder(new Random(seed));
            TabuSearch tabuSearch = new TabuSearch(evaluator, neighbourhood, TABU_TENURE, TABU_MAX_NO_IMPROVEMENT, decoder, seed).withVerbose(false);

            long start = System.currentTimeMillis();
            Schedule schedule = tabuSearch.solve(problem);
            long elapsed = System.currentTimeMillis() - start;

            int makespan = ScheduleValidator.makespan(schedule);
            ValidationResult result = validator.validateSchedule(problem, schedule);
            if (!result.valid()) {
                invalidCount++;
            }

            makespans.add(makespan);
            timesMs.add(elapsed);
            System.out.println(seed + "," + makespan + "," + elapsed + "," + result.valid());
        }

        System.out.println();
        printSummary(makespans, timesMs, invalidCount, beamMakespan);
    }

    private static void printSummary(List<Integer> makespans, List<Long> timesMs, int invalidCount, int beamMakespan) {
        double meanMakespan = mean(makespans);
        double stdDevMakespan = stdDev(makespans, meanMakespan);
        int minMakespan = makespans.stream().min(Integer::compareTo).orElseThrow();
        int maxMakespan = makespans.stream().max(Integer::compareTo).orElseThrow();
        double meanTime = timesMs.stream().mapToLong(Long::longValue).average().orElse(0);

        System.out.println("Tabu Search summary over " + makespans.size() + " runs:");
        System.out.printf(" min makespan:  %d%n", minMakespan);
        System.out.printf(" max makespan: %d%n", maxMakespan);
        System.out.printf(" mean makespan: %.1f%n", meanMakespan);
        System.out.printf(" std dev:  %.1f%n", stdDevMakespan);
        System.out.printf(" mean time (ms):  %.1f%n", meanTime);
        System.out.printf(" invalid schedules: %d / %d%n", invalidCount, makespans.size());
        System.out.println();
        System.out.printf("Comparison: Beam Search makespan = %d, Tabu Search best = %d, Tabu Search mean = %.1f%n",
                beamMakespan, minMakespan, meanMakespan);
    }

    private static double mean(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private static double stdDev(List<Integer> values, double mean) {
        double sumSquaredDiff = 0;
        for (int value : values) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        return Math.sqrt(sumSquaredDiff / values.size());
    }
}