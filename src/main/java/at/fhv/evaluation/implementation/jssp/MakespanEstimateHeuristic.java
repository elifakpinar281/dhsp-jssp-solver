package at.fhv.evaluation.implementation.jssp;

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
        int jobBound = 0;
        double penalty = 0.0;

        for (int jobId = 0; jobId < jsspProblem.getJobs().size(); jobId++) {
            List<Operation> operations = jsspProblem.getJobs().get(jobId).operations();
            int nextIndex = state.nextOperation()[jobId];
            int jobAvailable = state.jobAvailableTime()[jobId];
            int remaining = jobAvailable;

            for (int i = nextIndex; i < operations.size(); i++) {
                remaining += operations.get(i).processingTime();
            }

            jobBound = Math.max(jobBound, remaining);
            penalty += calculateDwellPenalty(operations, nextIndex, jobAvailable, state);
        }

        int machineBound = calculateMachineBound(state);
        return Math.max(jobBound, machineBound) + penalty;
    }

    private int calculateMachineBound(State state) {
        int machineCount = jsspProblem.getMachines().size();
        int[] remainingLoad = new int[machineCount];

        for (int jobId = 0; jobId < jsspProblem.getJobs().size(); jobId++) {
            List<Operation> operations = jsspProblem.getJobs().get(jobId).operations();
            for (int i = state.nextOperation()[jobId]; i < operations.size(); i++) {
                Operation operation = operations.get(i);
                remainingLoad[operation.machineId()] += operation.processingTime();
            }
        }

        int machineBound = 0;
        for (int machineId = 0; machineId < machineCount; machineId++) {
            if (remainingLoad[machineId] == 0) { continue; }

            int capacity = Math.max(1, jsspProblem.capacityOf(machineId));
            int offset = jsspProblem.bathOffset(machineId);

            long freeSum = 0;
            for (int bath = offset; bath < offset + capacity; bath++) {
                int free = state.bathAvailableTime()[bath];
                if (free == State.BLOCKED) { free = 0; }
                freeSum += free;
            }

            int estimate = (int) ((freeSum + remainingLoad[machineId]) / capacity);
            machineBound = Math.max(machineBound, estimate);
        }
        return machineBound;
    }

    private double calculateDwellPenalty(List<Operation> operations, int nextIndex, int jobAvailable, State state) {
        if (nextIndex <= 0 || nextIndex >= operations.size()) { return 0.0; }
        Operation predecessor = operations.get(nextIndex - 1);
        if (!predecessor.hasDwellLimit()) { return 0.0; }
        Operation nextOperation = operations.get(nextIndex);
        int earliestStart = Math.max(jobAvailable, jsspProblem.earliestBathTime(state, nextOperation.machineId()));

        int predecessorStart = jobAvailable - predecessor.processingTime();
        int deadline = predecessorStart + predecessor.maxDwellTime();
        int remainingDwell = deadline - earliestStart;

        if (remainingDwell <= 0) { return weight * 10000; }

        return weight * (1.0 / remainingDwell) * 1000;
    }
}