package at.fhv;

import at.fhv.solver.algorithms.ISearchAlgorithm;
import at.fhv.solver.algorithms.implementation.GreedyBestFirstSearch;
import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Parser parser = new Parser();
        JsspProblem jsspProblem = parser.parse("src/main/resources/benchmarks/ft06.txt");
        ISearchAlgorithm algorithm = new GreedyBestFirstSearch(new MakespanEstimateHeuristic());

        Schedule schedule = algorithm.solve(jsspProblem);
        System.out.println("Schedule: " + schedule);

    }
}