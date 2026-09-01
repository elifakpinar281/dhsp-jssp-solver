package at.fhv.solver;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;

public interface ISearchAlgorithm {
    Schedule solve(SchedulingProblem problem);
}
