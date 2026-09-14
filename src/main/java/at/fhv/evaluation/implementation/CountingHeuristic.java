package at.fhv.evaluation.implementation;

import at.fhv.evaluation.IHeuristic;
import at.fhv.solver.State;

public class CountingHeuristic implements IHeuristic {
    private final IHeuristic heuristic;
    private int count = 0;

    public CountingHeuristic(IHeuristic heuristic) {
        this.heuristic = heuristic;
    }

    @Override
    public double evaluate(State state) {
        count = count + 1;
        return heuristic.evaluate(state);
    }

    public int getCount() {
        return count;
    }
}
