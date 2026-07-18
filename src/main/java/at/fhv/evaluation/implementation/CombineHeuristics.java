package at.fhv.evaluation.implementation;

import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.Slack;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.solver.State;

public class CombineHeuristics implements IHeuristic {

    private final IHeuristic makespan;
    private final IHeuristic dwell;
    private final IHeuristic start;

    private final double weightDwell;
    private final double weightStart;

    public CombineHeuristics(JsspProblem problem) {
        Slack slack = new Slack(problem);
        this.makespan = new MakespanEstimateHeuristic(problem);
        this.dwell = new NextTMaxHeuristic(problem, slack);
        this.start = new NextTStartHeuristic(problem);
        this.weightDwell = 1.0;
        this.weightStart = 0.01;
    }

    @Override
    public double evaluate(State state) {
        double m = makespan.evaluate(state);
        double d = normalize(dwell.evaluate(state));
        double s = normalize(start.evaluate(state));
        return m + weightDwell * d + weightStart * s;
    }

    private double normalize(double x) {
        return x / (1.0 + x);
    }
}