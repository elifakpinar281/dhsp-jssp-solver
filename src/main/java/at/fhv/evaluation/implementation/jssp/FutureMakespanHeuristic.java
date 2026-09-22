package at.fhv.evaluation.implementation.jssp;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Operation;
import at.fhv.solver.State;

import java.util.List;


public class FutureMakespanHeuristic implements IHeuristic {
    private final int jobCount;
    private final int machineCount;
    private final int[][] operationMachine;
    private final int[][] operationTime;
    private final int[][] remainingTime;
    private final int[] capacity;
    private final int[] offset;

    public FutureMakespanHeuristic(JsspProblem jsspProblem) {
        this.jobCount = jsspProblem.getJobs().size();
        this.machineCount = jsspProblem.getMachines().size();

        this.operationMachine = new int[jobCount][];
        this.operationTime = new int[jobCount][];
        this.remainingTime = new int[jobCount][];

        for (int job = 0; job < jobCount; job++) {
            List<Operation> operations = jsspProblem.getJobs().get(job).operations();
            int count = operations.size();
            operationMachine[job] = new int[count];
            operationTime[job] = new int[count];
            remainingTime[job] = new int[count + 1];

            for (int i = 0; i < count; i++) {
                operationMachine[job][i] = operations.get(i).machineId();
                operationTime[job][i] = operations.get(i).processingTime();
            }
            for (int i = count - 1; i >= 0; i--) {
                remainingTime[job][i] = remainingTime[job][i + 1] + operationTime[job][i];
            }
        }

        this.capacity = new int[machineCount];
        this.offset = new int[machineCount];
        for (int machine = 0; machine < machineCount; machine++) {
            capacity[machine] = Math.max(1, jsspProblem.capacityOf(machine));
            offset[machine] = jsspProblem.bathOffset(machine);
        }
    }

    @Override
    public double evaluate(State state) {
        int past = pastMakespan(state);
        int[] nextOperation = state.nextOperation();
        int[] jobAvailable = state.jobAvailableTime();

        // Job-Bound und Restlast pro Maschine in einer Schleife
        int jobBound = 0;
        int[] remainingLoad = new int[machineCount];
        for (int job = 0; job < jobCount; job++) {
            int nextIndex = nextOperation[job];
            int completion = jobAvailable[job] + remainingTime[job][nextIndex];
            if (completion > jobBound) { jobBound = completion; }

            int[] machines = operationMachine[job];
            int[] times = operationTime[job];
            for (int i = nextIndex; i < machines.length; i++) {
                remainingLoad[machines[i]] += times[i];
            }
        }

        int machineBound = calculateMachineBound(state, remainingLoad);

        int totalBound = Math.max(jobBound, machineBound);
        int remaining = totalBound - past;
        return remaining > 0 ? remaining : 0;
    }

    // g: spätestes Ende einer bereits eingeplanten Operation
    private int pastMakespan(State state) {
        int makespan = 0;
        for (int available : state.jobAvailableTime()) { // jobAvailableTime -> wann ist der Job wieder bereit
            if (available == State.BLOCKED) { continue; }
            if (available > makespan) { makespan = available; }
        }
        return makespan;
    }

    // Pro Maschine bereits belegte Zeit der Bäder + Rest -> gleichmäßig auf die Bäder verteilt
    private int calculateMachineBound(State state, int[] remainingLoad) {
        int[] bathAvailable = state.bathAvailableTime();
        int machineBound = 0;

        for (int machine = 0; machine < machineCount; machine++) {
            if (remainingLoad[machine] == 0) { continue; }

            long freeSum = 0;
            for (int bath = offset[machine]; bath < offset[machine] + capacity[machine]; bath++) {
                int free = bathAvailable[bath];
                if (free == State.BLOCKED) { free = 0; }
                freeSum += free;
            }

            int estimate = (int) ((freeSum + remainingLoad[machine]) / capacity[machine]);
            if (estimate > machineBound) { machineBound = estimate; }
        }
        return machineBound;
    }
}