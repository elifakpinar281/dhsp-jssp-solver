package at.fhv.model.jssp;

import at.fhv.solver.State;

import java.util.ArrayList;
import java.util.List;

public class JsspProblem {
    private List<Machine> machines;
    private List<Job> jobs;

    public JsspProblem(List<Machine> machines, List<Job> jobs) {
        this.machines = machines;
        this.jobs = jobs;
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
                Operation nextOperation = job.operations().get(nextOpIndex);
                availableOperations.add(nextOperation);
            }
        }
        return availableOperations;
    }

    public Transition applyOperation(State state, Operation operation) {
        int jobId = operation.jobId();
        int machineId = operation.machineId();
        int processingTime = operation.processingTime();
        int opIndex = state.nextOperation()[jobId];

        int startTime = Math.max(state.machineAvailableTime()[machineId], state.jobAvailableTime()[jobId]);

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

        int[] newNextOperation = state.nextOperation().clone();
        int[] newMachineAvailableTime = state.machineAvailableTime().clone();
        int[] newJobAvailableTime = state.jobAvailableTime().clone();

        int endTime = startTime+processingTime;

        newNextOperation[jobId] = state.nextOperation()[jobId] + 1;
        newMachineAvailableTime[machineId] = endTime;
        newJobAvailableTime[jobId] = endTime;

        State newState = new State(newNextOperation, newMachineAvailableTime, newJobAvailableTime);
        ScheduledOperation scheduledOperation = new ScheduledOperation(jobId, machineId, startTime, endTime);
        return new Transition(newState, scheduledOperation);
    }

    public Operation jobPredecessor(Operation operation) {
        if (operation == null ) {return null; }

        for (Job job : jobs) {
            if (job.jobId() == operation.jobId()) {
                int i = job.operations().indexOf(operation);
                if (i <= 0) {return null; }
                return job.operations().get(i - 1);
            }
        }
        return null;
    }

    public Operation jobSuccessor(Operation operation) {
        if (operation == null ) { return null; }

        for (Job job : jobs) {
            if (job.jobId() == operation.jobId()) {
                int i = job.operations().indexOf(operation);
                if (i == -1 || i == job.operations().size()-1) { return null; }
                return job.operations().get(i + 1);
            }
        }
        return null;
    }



    public List<Machine> getMachines() {
        return machines;
    }

    public List<Job> getJobs() {
        return jobs;
    }
}
