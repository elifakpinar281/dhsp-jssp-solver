package at.fhv;

import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.decoder.impl.RandomStartDecoder;
import at.fhv.solver.implementation.BeamSearch;
import at.fhv.solver.implementation.GreedySearch;
import at.fhv.solver.implementation.tabu.INeighbourhood;
import at.fhv.solver.implementation.tabu.ScheduleEvaluator;
import at.fhv.solver.implementation.tabu.TabuSearch;
import at.fhv.solver.implementation.tabu.impl.N5Neighbourhood;
import at.fhv.solver.validation.MemorySampler;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import at.fhv.visualization.SearchStatistics;

import java.io.IOException;
import java.util.Random;

public class Main {
    private enum Algorithm { GREEDY, BEAM, TABU }

    private static final Algorithm ALGORITHM = Algorithm.BEAM;
    private static final String INSTANCE = "benchmarks/ft06.txt";

    private static final int BEAM_WIDTH = 20;

    private static final int TABU_TENURE = 10;
    private static final int TABU_MAX_NO_IMPROVEMENT = 500;
    private static final long TABU_SEED = 42;

    public static void main(String[] args) throws IOException {
        Parser parser = new Parser();
        JsspProblem problem = parser.parse(INSTANCE);
        IHeuristic heuristic = new MakespanEstimateHeuristic(problem);
        ScheduleValidator validator = new ScheduleValidator();

        ISearchAlgorithm solver = buildSolver(ALGORITHM, problem, heuristic);

        MemorySampler memorySampler = new MemorySampler();
        memorySampler.start();
        long start = System.currentTimeMillis();

        Schedule schedule = solver.solve(problem);

        long elapsed = System.currentTimeMillis() - start;
        memorySampler._stop();
        long peak = memorySampler.getPeak();
        System.out.println("algorithm,instance,makespan,peakHeap,timeMs,valid");

        if (schedule == null) {
            System.out.println(ALGORITHM + "," + INSTANCE + ",-,-," + elapsed + ",no solution");
            return;
        }

        ValidationResult result = validator.validateSchedule(problem, schedule);
        int makespan = ScheduleValidator.makespan(schedule);
        System.out.println(ALGORITHM + "," + INSTANCE + "," + makespan + "," + peak + "," + elapsed + "," + result.valid());

        if (!result.valid()) {
            for (String violation : result.violations()) {
                System.out.println("  " + violation);
            }
        }
    }

    private static ISearchAlgorithm buildSolver(Algorithm algorithm, JsspProblem problem, IHeuristic heuristic) {
        switch (algorithm) {
            case GREEDY:
                return new GreedySearch(heuristic);
            case BEAM:
                return new BeamSearch(heuristic, BEAM_WIDTH, new SearchStatistics());
            case TABU:
                ScheduleEvaluator evaluator = new ScheduleEvaluator(problem);
                INeighbourhood neighbourhood = new N5Neighbourhood();
                IStartDecoder decoder = new RandomStartDecoder(new Random(TABU_SEED));
                return new TabuSearch(evaluator, neighbourhood, TABU_TENURE, TABU_MAX_NO_IMPROVEMENT, decoder);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
}