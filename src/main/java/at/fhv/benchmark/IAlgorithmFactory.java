package at.fhv.benchmark;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.visualization.SearchStatistics;

public interface IAlgorithmFactory {
    ISearchAlgorithm create(JsspProblem jsspProblem, SearchStatistics searchStatistics);
}
