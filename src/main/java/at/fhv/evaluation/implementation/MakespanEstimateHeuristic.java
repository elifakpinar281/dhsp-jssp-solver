package at.fhv.evaluation.implementation;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Operation;
import at.fhv.solver.State;

import java.util.List;

public class MakespanEstimateHeuristic implements IHeuristic {
    private final JsspProblem jsspProblem;

    public MakespanEstimateHeuristic(JsspProblem jsspProblem) {
        this.jsspProblem = jsspProblem;
    }

    @Override
    public double evaluate(State state) {
        int maxRemaining = 0;

        for (int jobId = 0; jobId < jsspProblem.getJobs().size(); jobId++) {
            List<Operation> operations = jsspProblem.getJobs().get(jobId).operations();
            int remaining = state.jobAvailableTime()[jobId];

            for (int i = state.nextOperation()[jobId]; i<operations.size(); i++) {
                remaining = remaining + operations.get(i).processingTime();
            }

            if (remaining > maxRemaining) {
                maxRemaining = remaining;
            }
        }
        return (double) maxRemaining;
    }
}
