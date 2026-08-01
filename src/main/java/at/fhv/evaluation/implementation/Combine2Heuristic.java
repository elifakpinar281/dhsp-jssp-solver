package at.fhv.evaluation.implementation;

import at.fhv.evaluation.IHeuristic;
import at.fhv.solver.State;

import java.util.List;

public class Combine2Heuristic implements IHeuristic {
    private static final double TIE_BREAK_SCALE = 1e-3;

    private final IHeuristic makespan;
    private final List<IHeuristic> tieBreaks;

    public Combine2Heuristic(IHeuristic makespan, List<IHeuristic> tieBreaks) {
        this.makespan = makespan;
        this.tieBreaks = tieBreaks;
    }

    @Override
    public double evaluate(State state) {
        double value = makespan.evaluate(state);
        double scale = TIE_BREAK_SCALE;
        for (IHeuristic tieBreak : tieBreaks) {
            value += scale * normalize(tieBreak.evaluate(state));
            scale *= TIE_BREAK_SCALE;
        }
        return value;
    }

    private double normalize(double x) {
        double magnitude = Math.abs(x);
        double normalized = magnitude / (1.0 + magnitude);
        return x < 0 ? -normalized : normalized;
    }
}
