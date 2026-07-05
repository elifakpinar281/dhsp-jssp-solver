package at.fhv.solver.algorithms;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;

public interface ISearchAlgorithm {
    Schedule solve(JsspProblem jsspProblem);
}
