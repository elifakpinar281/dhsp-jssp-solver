package at.fhv.evaluation.implementation.fjssp;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.fjssp.FjsspOperation;
import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.solver.State;

import java.util.List;

public class FjsspMakespanHeuristic implements IHeuristic {
    private final FjsspProblem problem;
    private final int jobCount;
    private final int stationCount;
    private final int[][] operationStation;
    private final int[][] minimalTime;
    private final int[][] remainingMinimalTime;
    private final int[] capacity;
    private final int[] offset;

    public FjsspMakespanHeuristic(FjsspProblem problem) {
        this.problem = problem;
        this.jobCount = problem.getJobs().size();
        this.stationCount = problem.stationCount();
        this.operationStation = new int[jobCount][];
        this.minimalTime = new int[jobCount][];
        this.remainingMinimalTime = new int[jobCount][];

        for (int job = 0; job < jobCount; job++) {
            List<FjsspOperation> operations = problem.getJobs().get(job).operations();
            int count = operations.size();
            operationStation[job] = new int[count];
            minimalTime[job] = new int[count];
            remainingMinimalTime[job] = new int[count + 1];

            for (int i = 0; i < count; i++) {
                operationStation[job][i] = operations.get(i).stationId();
                minimalTime[job][i] = operations.get(i).minimalProcessingTime();
            }
            for (int i = count - 1; i >= 0; i--) {
                remainingMinimalTime[job][i] = remainingMinimalTime[job][i + 1] + minimalTime[job][i];
            }
        }

        this.capacity = new int[stationCount];
        this.offset = new int[stationCount];
        for (int station = 0; station < stationCount; station++) {
            capacity[station] = Math.max(1, problem.stationCapacity(station));
            offset[station] = problem.stationOffset(station);
        }
    }

    // jobbound -> für jeden Job die mögliche Startzeit (jobAvailableTime) + minimale processing time aller noch offenen Operationen
    // stationBound -> für jede Station verbleibende minimal processing time aller offenen Operation berechnen und auf parallele Bäder der Station verteilt
    // schauen wann die Bäder wieder verfügbar sind
    // h(s) = max(jobBound, stationBound)
    @Override
    public double evaluate(State state) {
        int[] nextOperation = state.nextOperation();
        int[] jobAvailable = state.jobAvailableTime();
        int jobBound = 0;
        long[] remainingLoad = new long[stationCount];

        for (int job = 0; job < jobCount; job++) {
            int nextIndex = nextOperation[job];
            jobBound = Math.max(jobBound, jobAvailable[job] + remainingMinimalTime[job][nextIndex]);

            int[] stations = operationStation[job];
            int[] times = minimalTime[job];
            for (int i = nextIndex; i < stations.length; i++) {
                remainingLoad[stations[i]] += times[i];
            }
        }

        return Math.max(jobBound, machineBound(state, remainingLoad));
    }

    private int machineBound(State state, long[] remainingLoad) {
        int[] bathAvailable = state.bathAvailableTime();
        int bound = 0;

        for (int station = 0; station < stationCount; station++) {
            if (remainingLoad[station] == 0) { continue; }

            long freeSum = 0;
            for (int bath = offset[station]; bath < offset[station] + capacity[station]; bath++) {
                int free = bathAvailable[bath];
                if (free == State.BLOCKED) { free = 0; }
                freeSum += free;
            }

            int estimate = (int) ((freeSum + remainingLoad[station]) / capacity[station]);
            bound = Math.max(bound, estimate);
        }
        return bound;
    }
}
