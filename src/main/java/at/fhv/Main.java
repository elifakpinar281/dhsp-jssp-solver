package at.fhv;

import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.implementation.GreedyBestFirstSearch;
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
        Schedule schedule = algorithm.solve(jsspProblem);

        int makespan = 0;
        for (var op : schedule.operations()) {
            makespan = Math.max(makespan, op.endTime());
        }
        System.out.println("Goal reached. Makespan: " + makespan);
    }
}