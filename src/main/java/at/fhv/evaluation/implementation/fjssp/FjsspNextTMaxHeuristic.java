package at.fhv.evaluation.implementation.fjssp;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.fjssp.FjsspJob;
import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.solver.State;

public class FjsspNextTMaxHeuristic implements IHeuristic {
    private static final int WEIGHT = 1000; // wie stark slack Bewertung gewichtet wird
    private final FjsspProblem problem;
    private final FjsspSlack slack;

    public FjsspNextTMaxHeuristic(FjsspProblem problem, FjsspSlack slack) {
        this.problem = problem;
        this.slack = slack;
    }

    // Für jeden Job nächste Operation nehmen, Slack berechnen. Kleiner Slack -> kritische Operation
    @Override
    public double evaluate(State state) {
        int weight = 0;

        for (FjsspJob fjsspJob : problem.getJobs()) {
            int s = slack.slackOf(fjsspJob.jobId(), state.nextOperation()[fjsspJob.jobId()]);
            if (s == FjsspSlack.NO_BINDING) { continue; }
            if (s <= 0) { weight += WEIGHT * 100; }
            else { weight += WEIGHT / s; } // s = 0 -> 1000 * 100 = 100000 -> hohe priorität
        }
        return weight;
    }

}
