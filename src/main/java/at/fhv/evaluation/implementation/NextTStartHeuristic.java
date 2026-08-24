package at.fhv.evaluation.implementation;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Operation;
import at.fhv.solver.State;

public class NextTStartHeuristic implements IHeuristic {
    private final double PENALTY_BLOCKED;
    private JsspProblem jsspProblem;

    public NextTStartHeuristic(JsspProblem jsspProblem) {
        this.jsspProblem = jsspProblem;
        PENALTY_BLOCKED = 1e9;
    }

    @Override
    public double evaluate(State state) {
        int total = 0;

        for (Job job : jsspProblem.getJobs()) {
            int nextIndex = state.nextOperation()[job.jobId()];

            if (nextIndex >= job.operations().size() ) { continue; }
            Operation nextOperation = job.operations().get(nextIndex);
            int start = earliestStartOf(state, job.jobId(), nextOperation);
            if (start == State.BLOCKED) { return PENALTY_BLOCKED; }
            total = total + start;
        }

        return total;
    }

    private int earliestStartOf(State state, int jobId, Operation nextOperation) {
        int jobReady = state.jobAvailableTime()[jobId];

        int bathReady = jsspProblem.earliestBathTime(state, nextOperation.machineId());
        if (bathReady == State.BLOCKED) { return State.BLOCKED; }

        return Math.max(jobReady, bathReady);
    }
}