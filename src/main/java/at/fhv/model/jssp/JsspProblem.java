package at.fhv.model.jssp;

import at.fhv.solver.State;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsspProblem {
    private final List<Machine> machines;
    private final List<Job> jobs;

    private final int[] capacity;
    private final int[] bathOffset;
    private final int totalBaths;

    private final Map<Operation, Operation> jobPredecessor;
    private final Map<Operation, Operation> jobSuccessor;

    public JsspProblem(List<Machine> machines, List<Job> jobs) {
        this.machines = machines;
        this.jobs = jobs;

        this.capacity = new int[machines.size()];
        this.bathOffset = new int[machines.size()];

        int offset = 0;
        for (int i = 0; i < machines.size(); i++) {
            capacity[machines.get(i).machineId()] = machines.get(i).capacity();
        }
        for (int machineId = 0; machineId < machines.size(); machineId++) {
            bathOffset[machineId] = offset;
            offset += capacity[machineId];
        }
        this.totalBaths = offset;

        this.jobPredecessor = new HashMap<>();
        this.jobSuccessor = new HashMap<>();
        for (Job job : jobs) {
            List<Operation> operations = job.operations();
            for (int i = 0; i < operations.size(); i++) {
                if (i > 0) { jobPredecessor.put(operations.get(i), operations.get(i - 1)); }
                if (i < operations.size() - 1) { jobSuccessor.put(operations.get(i), operations.get(i + 1)); }
            }
        }
    }

    public int capacityOf(int machineId) {
        return capacity[machineId];
    }

    public int bathOffset(int machineId) {
        return bathOffset[machineId];
    }

    public int totalBaths() {
        return totalBaths;
    }

    public int earliestFreeBath(State state, int machineId) {
        int offset = bathOffset[machineId];
        int best = offset;

        for (int bath = offset + 1; bath < offset + capacity[machineId]; bath++) {
            if (state.bathAvailableTime()[bath] < state.bathAvailableTime()[best]) {
                best = bath;
            }
        }
        return best;
    }

    public int earliestBathTime(State state, int machineId) {
        return state.bathAvailableTime()[earliestFreeBath(state, machineId)];
    }

    public boolean isGoal(State state) {
        for (int jobId = 0; jobId < jobs.size(); jobId++) {
            if (state.nextOperation()[jobId] < jobs.get(jobId).operations().size()) {
                return false;
            }
        }
        return true;
    }

    public List<Operation> getAvailableOperations(State state) {
        List<Operation> availableOperations = new ArrayList<>();

        for (Job job : jobs) {
            int jobId = job.jobId();
            int nextOpIndex = state.nextOperation()[jobId];

            if (nextOpIndex < job.operations().size()) {
                availableOperations.add(job.operations().get(nextOpIndex));
            }
        }
        return availableOperations;
    }

    public Transition applyOperation(State state, Operation operation) {
        int jobId = operation.jobId();
        int machineId = operation.machineId();
        int opIndex = state.nextOperation()[jobId];

        int bath = earliestFreeBath(state, machineId);
        int startTime = Math.max(state.bathAvailableTime()[bath], state.jobAvailableTime()[jobId]);

        if (opIndex > 0) {
            Operation predecessor = jobs.get(jobId).operations().get(opIndex - 1);
            if (predecessor.hasDwellLimit()) {
                int predecessorStart = state.jobAvailableTime()[jobId] - predecessor.processingTime();
                int latestAdmissibleStart = predecessorStart + predecessor.maxDwellTime();
                if (startTime > latestAdmissibleStart) {
                    return null;
                }
            }
        }

        int endTime = startTime + operation.processingTime();

        int[] newNextOperation = state.nextOperation().clone();
        int[] newBathAvailableTime = state.bathAvailableTime().clone();
        int[] newJobAvailableTime = state.jobAvailableTime().clone();

        newNextOperation[jobId] = opIndex + 1;
        newBathAvailableTime[bath] = endTime;
        newJobAvailableTime[jobId] = endTime;

        State newState = new State(newNextOperation, newBathAvailableTime, newJobAvailableTime);
        ScheduledOperation scheduledOperation = new ScheduledOperation(jobId, machineId, startTime, endTime);
        return new Transition(newState, scheduledOperation);
    }

    public Operation jobPredecessor(Operation operation) {
        if (operation == null) { return null; }
        return jobPredecessor.get(operation);
    }

    public Operation jobSuccessor(Operation operation) {
        if (operation == null) { return null; }
        return jobSuccessor.get(operation);
    }

    public List<Machine> getMachines() {
        return machines;
    }

    public List<Job> getJobs() {
        return jobs;
    }
}