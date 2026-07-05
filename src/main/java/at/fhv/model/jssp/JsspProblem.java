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
                int machineId = nextOperation.machineId();

                if (state.machineAvailableTime()[machineId] <= state.jobAvailableTime()[jobId]) {
                    availableOperations.add(nextOperation);
                }
            }
        }

        return availableOperations;
    }

    public State applyOperation(State state, Operation operation) {
        int jobId = operation.jobId();
        int machineId = operation.machineId();
        int processingTime = operation.processingTime();

        int[] newNextOperation = state.nextOperation().clone();
        int[] newMachineAvailableTime = state.machineAvailableTime().clone();
        int[] newJobAvailableTime = state.jobAvailableTime().clone();

        List<ScheduledOperation> newSchedule = new ArrayList<>(state.scheduledOperations());

        int startTime = Math.max(state.machineAvailableTime()[machineId], state.jobAvailableTime()[jobId]);
        int endTime = startTime+processingTime;

        newMachineAvailableTime[machineId] = endTime;
        newJobAvailableTime[jobId] = endTime;

        newSchedule.add(new ScheduledOperation(jobId, machineId, startTime, endTime));
        return new State(newNextOperation, newMachineAvailableTime, newJobAvailableTime, newSchedule);
    }

    public List<Machine> getMachines() {
        return machines;
    }

    public List<Job> getJobs() {
        return jobs;
    }
}
