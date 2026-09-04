package at.fhv.evaluation.implementation.fjssp;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.fjssp.FjsspOperation;
import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.solver.State;

import java.util.List;

public class FjsspMakespanHeuristic implements IHeuristic {
    private final FjsspProblem problem;

    public FjsspMakespanHeuristic(FjsspProblem problem) {
        this.problem = problem;
    }

    // jobbound -> für jeden Job die mögliche Startzeit (jobAvailableTime) + minimale processing time aller noch offenen Operationen
    // stationBound -> für jede Station verbleibende minimal processing time aller offenen Operation berechnen und auf parallele Bäder der Station verteilt
    // schauen wann die Bäder wieder verfügbar sind
    // h(s) = max(jobBound, stationBound)
    @Override
    public double evaluate(State state) {
        int jobBound = 0;

        for (int jobId = 0; jobId < problem.getJobs().size(); jobId++) {
            List<FjsspOperation> operations = problem.getJobs().get(jobId).operations();
            int remaining = state.jobAvailableTime()[jobId];

            for (int i = state.nextOperation()[jobId]; i < operations.size(); i++) {
                remaining += operations.get(i).minimalProcessingTime();
            }
            jobBound = Math.max(jobBound, remaining);
        }

        return Math.max(jobBound, machineBound(state));
    }

    private int machineBound(State state) {
        int stationCount = problem.stationCount();
        long[] remainingLoad = new long[stationCount];

        for (int jobId = 0; jobId < problem.getJobs().size(); jobId++) {
            List<FjsspOperation> operations = problem.getJobs().get(jobId).operations();
            for (int i = state.nextOperation()[jobId]; i < operations.size(); i++) {
                FjsspOperation operation = operations.get(i);
                remainingLoad[operation.stationId()] += operation.minimalProcessingTime();
            }
        }

        int bound = 0;
        for (int station = 0; station < stationCount; station++) {
            if (remainingLoad[station] == 0) { continue; }

            int capacity = Math.max(1, problem.stationCapacity(station));
            int offset = problem.stationOffset(station);

            long freeSum = 0;
            for (int bath = offset; bath < offset + capacity; bath++) {
                int free = state.bathAvailableTime()[bath];
                if (free == State.BLOCKED) { free = 0; }
                freeSum += free;
            }

            int estimate = (int) ((freeSum + remainingLoad[station]) / capacity);
            bound = Math.max(bound, estimate);
        }
        return bound;
    }
}
