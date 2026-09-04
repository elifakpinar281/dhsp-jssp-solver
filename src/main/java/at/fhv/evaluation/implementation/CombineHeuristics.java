package at.fhv.evaluation.implementation;

import at.fhv.evaluation.IHeuristic;
import at.fhv.solver.State;

import java.util.List;

public class CombineHeuristics implements IHeuristic {
    private static final double TIE_BREAK_SCALE = 1e-3;

    private final IHeuristic primary;
    private final List<IHeuristic> tieBreaks;

    public CombineHeuristics(IHeuristic primary, List<IHeuristic> tieBreaks) {
        this.primary = primary;
        this.tieBreaks = tieBreaks;
    }

    @Override
    public double evaluate(State state) {
        double value = primary.evaluate(state);
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