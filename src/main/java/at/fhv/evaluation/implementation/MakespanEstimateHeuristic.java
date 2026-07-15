package at.fhv.evaluation.implementation;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Operation;
import at.fhv.solver.State;

import java.util.List;

public class MakespanEstimateHeuristic implements IHeuristic {
    private final JsspProblem jsspProblem;
    private final double weight;

    public MakespanEstimateHeuristic(JsspProblem jsspProblem) {
        this(jsspProblem, 1.0);
    }

    public MakespanEstimateHeuristic(JsspProblem jsspProblem, double weight) {
        this.jsspProblem = jsspProblem;
        this.weight = weight;
    }

    @Override
    public double evaluate(State state) {
        int maxRemaining = 0;
        double penalty = 0.0;


        for (int jobId = 0; jobId < jsspProblem.getJobs().size(); jobId++) {
            List<Operation> operations = jsspProblem.getJobs().get(jobId).operations();
            int nextIndex = state.nextOperation()[jobId];
            int jobAvailable = state.jobAvailableTime()[jobId];
            int remaining = jobAvailable;

            for (int i = nextIndex; i < operations.size(); i++) {
                remaining += operations.get(i).processingTime();
            }

            maxRemaining = Math.max(maxRemaining, remaining);
            penalty += calculateDwellPenalty(operations, nextIndex, jobAvailable, state);
        }
        return maxRemaining + penalty;
    }


    private double calculateDwellPenalty(List<Operation> operations, int nextIndex, int jobAvailable, State state) {
        if (nextIndex <= 0 || nextIndex >= operations.size()) { return 0.0; }
        Operation predecessor = operations.get(nextIndex - 1);
        if (!predecessor.hasDwellLimit()) { return 0.0; }
        Operation nextOperation = operations.get(nextIndex);
        int earliestStart = Math.max(jobAvailable, state.machineAvailableTime()[nextOperation.machineId()]);

        int deadline = jobAvailable + predecessor.maxDwellTime();
        int remainingDwell = deadline - earliestStart;

        if (remainingDwell <= 0) { return weight * 10000;}

        return weight * (1.0 / remainingDwell) * 1000;
    }
}