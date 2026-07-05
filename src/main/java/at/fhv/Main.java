package at.fhv;

import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.implementation.GreedyBestFirstSearch;
import at.fhv.visualization.SearchMetricsVisualizer;
import at.fhv.visualization.SearchStatistics;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Parser parser = new Parser();
        JsspProblem jsspProblem = parser.parse("benchmarks/la02.txt");
        SearchStatistics statistics = new SearchStatistics(1000, 0);

        GreedyBestFirstSearch algorithm = new GreedyBestFirstSearch(new MakespanEstimateHeuristic(jsspProblem), statistics);
        Schedule schedule = algorithm.solve(jsspProblem);

        int makespan = 0;
        for (var op : schedule.operations()) {
            makespan = Math.max(makespan, op.endTime());
        }
        System.out.println("Makespan: " + makespan);
        SearchMetricsVisualizer.show("GBFS", statistics);
    }
}