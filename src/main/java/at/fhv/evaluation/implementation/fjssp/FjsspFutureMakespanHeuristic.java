package at.fhv.evaluation.implementation.fjssp;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.solver.State;

public class FjsspFutureMakespanHeuristic implements IHeuristic {
    private final FjsspMakespanHeuristic totalBound;

    public FjsspFutureMakespanHeuristic(FjsspProblem problem) {
        this.totalBound = new FjsspMakespanHeuristic(problem);
    }

    @Override
    public double evaluate(State state) {
        double total = totalBound.evaluate(state);
        int past = pastMakespan(state);
        double remaining = total - past;
        return remaining > 0 ? remaining : 0;
    }

    // g -> spätestes Ende einer bereits eingeplanten Operation
    private int pastMakespan(State state) {
        int makespan = 0;
        for (int available : state.jobAvailableTime()) { // jobAvailableTime -> wann ist der job wieder erreichbar
            if (available == State.BLOCKED) { continue; }
            if (available > makespan) { makespan = available; }
        }
        return makespan;
    }
}
