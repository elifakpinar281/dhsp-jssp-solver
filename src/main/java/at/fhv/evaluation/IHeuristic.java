package at.fhv.evaluation;

import at.fhv.solver.State;

public interface IHeuristic {
    double evaluate(State state);
}
