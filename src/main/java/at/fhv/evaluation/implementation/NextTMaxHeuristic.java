package at.fhv.evaluation.implementation;

import at.fhv.evaluation.IHeuristic;
import at.fhv.evaluation.Slack;
import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.solver.State;

// kleiner Slack bedeutet hoher Wert (dringender)
// Es werden enge dwell times gemieden ist das okay? Sonst wären die einfach schlechter bewertet
public class NextTMaxHeuristic implements IHeuristic {
    private static final int WEIGHT = 1000;

    private final JsspProblem jsspProblem;
    private final Slack slack;

    public NextTMaxHeuristic(JsspProblem jsspProblem, Slack slack) {
        this.jsspProblem = jsspProblem;
        this.slack = slack;
    }

    @Override
    public double evaluate(State state) {
        int weight = 0;

        for (Job job : jsspProblem.getJobs()) {
            int s = slack.slackOf(job.jobId(), state.nextOperation()[job.jobId()]);
            if (s == Slack.NO_BINDING) { continue; }
            if (s <= 0) { weight += WEIGHT * 100; }
            else { weight += WEIGHT / s; }
        }

        return weight;
    }
}