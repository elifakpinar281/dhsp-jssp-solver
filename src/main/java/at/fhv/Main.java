package at.fhv;

import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.implementation.BeamSearch;
import at.fhv.solver.validation.MemorySampler;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import at.fhv.visualization.SearchStatistics;

import java.io.IOException;

public class Main {
    private static final String INSTANCE = "benchmarks/ft06.txt";
    private static final long SAMPLE_INTERVAL = 1;
    private static final String SAMPLE_DIR = "docs/assets/beam/";
    private static final int[] BEAM_WIDTHS = {1, 5, 20, 50, 100};

    public static void main(String[] args) throws IOException {
        Parser parser = new Parser();
        JsspProblem jsspProblem = parser.parse(INSTANCE);
        IHeuristic heuristic = new MakespanEstimateHeuristic(jsspProblem);
        ScheduleValidator validator = new ScheduleValidator();

        System.out.println("k,makespan,peakHeap,valid");

        for (int beamWidth : BEAM_WIDTHS) {
            SearchStatistics statistics = new SearchStatistics(SAMPLE_INTERVAL, 0);
            statistics.enableLog(SAMPLE_DIR + "beam-ft06-k" + beamWidth + "-samples.csv");
            BeamSearch algorithm = new BeamSearch(heuristic, beamWidth, statistics);

            MemorySampler memorySampler = new MemorySampler();
            memorySampler.start();

            Schedule schedule = algorithm.solve(jsspProblem);

            memorySampler._stop();
            long peak = memorySampler.getPeak();

            if (schedule == null) {
                System.out.println(beamWidth + ",-,-,no solution");
                continue;
            }

            ValidationResult result = validator.validateSchedule(jsspProblem, schedule);
            int makespan = ScheduleValidator.makespan(schedule);

            System.out.println(beamWidth + "," + makespan + "," + peak + "," + result.valid());

            if (!result.valid()) {
                for (String violation : result.violations()) {
                    System.out.println("  " + violation);
                }
            }

            schedule = null;
            System.gc();
        }
    }
}