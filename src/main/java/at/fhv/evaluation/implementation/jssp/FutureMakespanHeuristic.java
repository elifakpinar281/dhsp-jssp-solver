package at.fhv.evaluation.implementation.jssp;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Operation;
import at.fhv.solver.State;

import java.util.List;

public class FutureMakespanHeuristic implements IHeuristic {
    private final JsspProblem jsspProblem;

    public FutureMakespanHeuristic(JsspProblem jsspProblem) {
        this.jsspProblem = jsspProblem;
    }

    @Override
    public double evaluate(State state) {
        int past = pastMakespan(state);
        int jobBound = calculateJobBound(state);
        int machineBound = calculateMachineBound(state);

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

    // Pro Job aktuelle Verfügbarkeit + Summe der restlichen Bearbeitungszeiten
    private int calculateJobBound(State state) {
        int jobBound = 0;

        for (int jobId = 0; jobId < jsspProblem.getJobs().size(); jobId++) {
            List<Operation> operations = jsspProblem.getJobs().get(jobId).operations();
            int nextIndex = state.nextOperation()[jobId];

            int completion = state.jobAvailableTime()[jobId];
            for (int i = nextIndex; i < operations.size(); i++) {
                completion += operations.get(i).processingTime();
            }

            if (completion > jobBound) { jobBound = completion; }
        }
        return jobBound;
    }

    // Pro Maschine breits belegte Zeit der Bäder + Rest -> gleichmäßig auf die Bäder verteilt
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
            if (estimate > machineBound) { machineBound = estimate; }
        }
        return machineBound;
    }
}