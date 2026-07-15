package at.fhv.model.jssp;

import at.fhv.solver.State;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsspProblem {
    private final List<Machine> machines;
    private final List<Job> jobs;

    private final boolean blocking;

    private final int[] capacity;
    private final int[] bathOffset;
    private final int totalBaths;

    private final Map<Operation, Operation> jobPredecessor;
    private final Map<Operation, Operation> jobSuccessor;

    public JsspProblem(List<Machine> machines, List<Job> jobs) {
        this(machines, jobs, false);
    }

    public JsspProblem(List<Machine> machines, List<Job> jobs, boolean blocking) {
        this.machines = machines;
        this.jobs = jobs;
        this.blocking = blocking;

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

    public boolean isBlocking() {
        return blocking;
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
        return earliestFreeBath(state.bathAvailableTime(), machineId);
    }

    public int earliestBathTime(State state, int machineId) {
        int bath = earliestFreeBath(state, machineId);
        if (bath == State.NO_BATH) { return State.BLOCKED; }
        return state.bathAvailableTime()[bath];
    }

    private int earliestFreeBath(int[] bathAvailableTime, int machineId) {
        int best = State.NO_BATH;

        for (int bath = bathOffset[machineId]; bath < bathOffset[machineId] + capacity[machineId]; bath++) {
            if (bathAvailableTime[bath] == State.BLOCKED) { continue; }
            if (best == State.NO_BATH || bathAvailableTime[bath] < bathAvailableTime[best]) {
                best = bath;
            }
        }
        return best;
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

    public List<Operation> dwellChain(int jobId, int firstIndex) {
        List<Operation> operations = jobs.get(jobId).operations();
        List<Operation> chain = new ArrayList<>();

        int index = firstIndex;
        while (index < operations.size()) {
            Operation operation = operations.get(index);
            chain.add(operation);
            if (!operation.hasDwellLimit()) { break; }
            index = index + 1;
        }
        return chain;
    }

    public Transition applyOperation(State state, Operation operation) {
        int jobId = operation.jobId();
        int firstIndex = state.nextOperation()[jobId];

        if (firstIndex >= jobs.get(jobId).operations().size()) { return null; }

        List<Operation> chain = dwellChain(jobId, firstIndex);

        int headBath = earliestFreeBath(state, chain.get(0).machineId());
        if (headBath == State.NO_BATH) { return null; }

        int startTime = Math.max(state.jobAvailableTime()[jobId], state.bathAvailableTime()[headBath]);

        ChainPlacement placement = null;
        for (int attempt = 0; attempt <= chain.size(); attempt++) {
            ChainPlacement attemptPlacement = placeChain(state, jobId, chain, startTime);
            if (attemptPlacement.feasible()) {
                placement = attemptPlacement;
                break;
            }
            if (attemptPlacement.blocked()) { return null; }
            startTime = attemptPlacement.requiredStart();
        }

        if (placement == null) {
            startTime = latestBathStart(state, jobId, chain);
            if (startTime == State.BLOCKED) { return null; }

            ChainPlacement fallback = placeChain(state, jobId, chain, startTime);
            if (!fallback.feasible()) { return null; }
            placement = fallback;
        }

        return commit(state, jobId, chain, placement);
    }

    private int latestBathStart(State state, int jobId, List<Operation> chain) {
        int startTime = state.jobAvailableTime()[jobId];
        for (Operation operation : chain) {
            int bathTime = earliestBathTime(state, operation.machineId());
            if (bathTime == State.BLOCKED) { return State.BLOCKED; }
            startTime = Math.max(startTime, bathTime);
        }
        return startTime;
    }

    private ChainPlacement placeChain(State state, int jobId, List<Operation> chain, int chainStart) {
        int[] bathAvailableTime = state.bathAvailableTime().clone();
        int[] baths = new int[chain.size()];
        int[] startTimes = new int[chain.size()];
        int[] endTimes = new int[chain.size()];

        int jobReady = chainStart;

        for (int i = 0; i < chain.size(); i++) {
            Operation operation = chain.get(i);
            int bath = earliestFreeBath(bathAvailableTime, operation.machineId());
            if (bath == State.NO_BATH) { return ChainPlacement.blockedByCarrier(); }

            int startTime = Math.max(jobReady, bathAvailableTime[bath]);

            if (i > 0) {
                Operation predecessor = chain.get(i - 1);
                int latestAdmissibleStart = startTimes[i - 1] + predecessor.maxDwellTime();
                if (startTime > latestAdmissibleStart) {
                    int shift = startTime - latestAdmissibleStart;
                    return ChainPlacement.infeasible(chainStart + shift);
                }
            }

            baths[i] = bath;
            startTimes[i] = startTime;
            endTimes[i] = startTime + operation.processingTime();
            bathAvailableTime[bath] = endTimes[i];
            jobReady = endTimes[i];
        }
        return ChainPlacement.feasible(baths, startTimes, endTimes);
    }

    private Transition commit(State state, int jobId, List<Operation> chain, ChainPlacement placement) {
        int[] newNextOperation = state.nextOperation().clone();
        int[] newBathAvailableTime = state.bathAvailableTime().clone();
        int[] newJobAvailableTime = state.jobAvailableTime().clone();
        int[] newJobBath = state.jobBath().clone();

        List<ScheduledOperation> scheduledOperations = new ArrayList<>();
        int lastIndex = chain.size() - 1;

        int firstIndex = state.nextOperation()[jobId];
        boolean jobFinished = firstIndex + chain.size() >= jobs.get(jobId).operations().size();

        if (blocking) {
            if (state.jobBath()[jobId] != State.NO_BATH) {
                newBathAvailableTime[state.jobBath()[jobId]] = placement.startTimes()[0];
            }

            for (int i = 0; i < lastIndex; i++) {
                newBathAvailableTime[placement.baths()[i]] = placement.startTimes()[i + 1];
            }

            if (jobFinished) {
                newBathAvailableTime[placement.baths()[lastIndex]] = placement.endTimes()[lastIndex];
                newJobBath[jobId] = State.NO_BATH;
            } else {
                newBathAvailableTime[placement.baths()[lastIndex]] = State.BLOCKED;
                newJobBath[jobId] = placement.baths()[lastIndex];
            }
        } else {
            for (int i = 0; i <= lastIndex; i++) {
                newBathAvailableTime[placement.baths()[i]] = placement.endTimes()[i];
            }
            newJobBath[jobId] = State.NO_BATH;
        }

        for (int i = 0; i < chain.size(); i++) {
            Operation operation = chain.get(i);
            scheduledOperations.add(new ScheduledOperation(jobId, operation.machineId(), placement.startTimes()[i], placement.endTimes()[i]));
        }

        newNextOperation[jobId] = firstIndex + chain.size();
        newJobAvailableTime[jobId] = placement.endTimes()[lastIndex];

        State newState = new State(newNextOperation, newBathAvailableTime, newJobAvailableTime, newJobBath);
        return new Transition(newState, scheduledOperations);
    }

    private record ChainPlacement(boolean feasible, boolean blocked, int requiredStart, int[] baths, int[] startTimes, int[] endTimes) {
        static ChainPlacement feasible(int[] baths, int[] startTimes, int[] endTimes) {
            return new ChainPlacement(true, false, 0, baths, startTimes, endTimes);
        }

        static ChainPlacement infeasible(int requiredStart) {
            return new ChainPlacement(false, false, requiredStart, null, null, null);
        }

        static ChainPlacement blockedByCarrier() {
            return new ChainPlacement(false, true, 0, null, null, null);
        }
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