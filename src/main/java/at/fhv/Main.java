package at.fhv;

import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.implementation.GreedyBestFirstSearch;
import at.fhv.solver.validation.MemorySampler;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import at.fhv.visualization.SearchStatistics;

import java.io.IOException;

public class Main {
    private static final String INSTANCE = "benchmarks/ft06.txt";
    private static final long SAMPLE_INTERVAL = 1000;
    private static final String SAMPLE_LOG = "docs/assets/gbfs-ft06-samples.csv";

    public static void main(String[] args) throws IOException {
        Parser parser = new Parser();
        JsspProblem jsspProblem = parser.parse(INSTANCE);

        SearchStatistics searchStatistics = new SearchStatistics(SAMPLE_INTERVAL, 0);
        searchStatistics.enableLog(SAMPLE_LOG);

        GreedyBestFirstSearch algorithm = new GreedyBestFirstSearch(new MakespanEstimateHeuristic(jsspProblem), searchStatistics);
        MemorySampler memorySampler = new MemorySampler();
        memorySampler.start();

        Schedule schedule = algorithm.solve(jsspProblem);

        memorySampler._stop();
        System.out.println("Peak-Heap: " + memorySampler.getPeak());

        if (schedule == null) {
            System.out.println("No solution found");
            return;
        }

        ScheduleValidator validator = new ScheduleValidator();
        ValidationResult result = validator.validateSchedule(jsspProblem, schedule);

        System.out.println("Validation result: " + result.summary());

        if (!result.valid()) {
            for (String violation: result.violations()) {
                System.out.println(violation);
            }
        }

        int makespan = ScheduleValidator.makespan(schedule);
        System.out.println("Goal reached. Makespan: " + makespan);
    }
}