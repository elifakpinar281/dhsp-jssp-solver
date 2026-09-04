package at.fhv.evaluation.implementation.fjssp;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.fjssp.FjsspJob;
import at.fhv.model.fjssp.FjsspOperation;
import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.solver.State;

// Unterschied zu JSSP -> Mehrere wählbare Bäder - Bad nehmen, das am frühesten frei ist
public class FjsspNextTStartHeuristic implements IHeuristic {
    private static final double PENALTY_BLOCKED = 1e9;
    private final FjsspProblem fjsspProblem;

    public FjsspNextTStartHeuristic(FjsspProblem fjsspProblem) {
        this.fjsspProblem = fjsspProblem;
    }

    @Override
    public double evaluate(State state) {
        int total = 0;

        for (FjsspJob job : fjsspProblem.getJobs()) {
            int nextIndex = state.nextOperation()[job.jobId()];
            if (nextIndex >= job.operations().size()) {
                continue;
            }

            FjsspOperation nextOperation = job.operations().get(nextIndex);
            int start = earliestStartOf(state, job.jobId(), nextOperation);
            if (start == State.BLOCKED) {
                return PENALTY_BLOCKED;
            }
            total = total + start;
        }
        return total;
    }

    // früheste Startzeit der nächsten Operation des Jobs unter Berücksichtigung der Verfügbarkeit des Jobs und der Bäder
    private int earliestStartOf(State state, int jobId, FjsspOperation nextOperation) {
        int jobReady = state.jobAvailableTime()[jobId];
        int bathReady = earliestBathTime(state.bathAvailableTime(), nextOperation);
        if (bathReady == State.BLOCKED) {
            return State.BLOCKED;
        }

        return Math.max(jobReady, bathReady);
    }

    // Früheste Verfügbarkeit unter erlaubten Bädern der Operation
    private int earliestBathTime(int[] bathAvailableTime, FjsspOperation operation) {
        int best = State.NO_BATH;
        for (int bath : operation.eligibleMachines()) {
            if (bathAvailableTime[bath] == State.BLOCKED) { continue; }
            if (best == State.NO_BATH || bathAvailableTime[bath] < bathAvailableTime[best]) { best = bath;}
        }
        if (best == State.NO_BATH) { return State.BLOCKED; }

        return bathAvailableTime[best];

    }
}