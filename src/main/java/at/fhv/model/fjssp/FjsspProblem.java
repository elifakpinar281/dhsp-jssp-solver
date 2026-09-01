package at.fhv.model.fjssp;

import at.fhv.model.jssp.Machine;
import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.model.jssp.Transition;
import at.fhv.solver.SchedulingProblem;
import at.fhv.solver.State;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FjsspProblem implements SchedulingProblem {
    private static final int MAX_MACHINE_CHOICES = 3; // Suchbaum kleiner halten?

    private final List<FjsspJob> jobs;
    private final boolean blocking;
    private final List<Machine> baths;
    private final int[] stationCapacity;
    private final int[] stationOffset;
    private final int stationCount;
    private final int totalBaths;

    public FjsspProblem(List<FjsspJob> jobs, int[] stationCapacity, boolean blocking) {
        this.jobs = jobs;
        this.blocking = blocking;
        this.stationCapacity = stationCapacity.clone();
        this.stationCount = stationCapacity.length;
        this.stationOffset = new int[stationCount];
        int offset = 0;
        for (int station = 0; station < stationCount; station++) {
            stationOffset[station] = offset;
            offset += stationCapacity[station];
        }
        this.totalBaths = offset;

        this.baths = new ArrayList<>();
        for (int bath = 0; bath < totalBaths; bath++) {
            baths.add(new Machine(bath, 1));
        }
    }

    @Override
    public State createInitialState() {
        int[] nextOperation = new int[jobs.size()];
        int[] bathAvailableTime = new int[totalBaths];
        int[] jobAvailableTime = new int[jobs.size()];
        int[] jobBath = new int[jobs.size()];
        Arrays.fill(jobBath, State.NO_BATH);
        return new State(nextOperation, bathAvailableTime, jobAvailableTime, jobBath);
    }

    @Override
    public boolean isGoal(State state) {
        for (int jobId = 0; jobId < jobs.size(); jobId++) {
            if (state.nextOperation()[jobId] < jobs.get(jobId).operations().size()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int totalOperations() {
        int total = 0;
        for (FjsspJob job : jobs) {
            total = total + job.operations().size();
        }
        return total;
    }

    @Override
    public List<Transition> expand(State state) {
        List<Transition> transitions = new ArrayList<>();

        for (FjsspJob job : jobs) {
            int jobId = job.jobId();
            int fIndex = state.nextOperation()[jobId];
            if (fIndex >= job.operations().size()) { continue; }

            List<FjsspOperation> chain = dwellChain(jobId, fIndex);
            FjsspOperation head = chain.get(0);

            for (int headBath : headBathCandidates(state.bathAvailableTime(), head)) {
                Transition transition = tryPlace(state, jobId, chain, headBath);
                if (transition != null) { transitions.add(transition); }
            }

        }
        return transitions;
    }

    // Wenn die Operation eine Max-Dwell-Time hat, muss die nächste innerhalb dieser Zeit anschließen - gemeinsam platzieren
    public List<FjsspOperation> dwellChain(int jobId, int firstIndex) {
        List<FjsspOperation> operations = jobs.get(jobId).operations();
        List<FjsspOperation> chain = new ArrayList<>();

        int index = firstIndex;
        while (index < operations.size()) {
            FjsspOperation operation = operations.get(index);
            chain.add(operation);
            if (!operation.hasDwellLimit()) { break; }
            index = index + 1;
        }
        return chain;
    }

    private List<Integer> headBathCandidates(int[] bathAvailableTime, FjsspOperation head) {
        List<Integer> freeBaths = new ArrayList<>();
        for (int bath : head.eligibleMachines()) {
            if (bathAvailableTime[bath] != State.BLOCKED) { freeBaths.add(bath); }
        }

        freeBaths.sort((a, b) -> {
            int timesorted = Integer.compare(bathAvailableTime[a], bathAvailableTime[b]);
            if (timesorted != 0) { return timesorted; }
            return Integer.compare(a, b);
        });

        List<Integer> candidates = new ArrayList<>();
        int lastAvailable = Integer.MIN_VALUE;
        for (int baths : freeBaths) {
            int available = bathAvailableTime[baths];
            if (candidates.isEmpty() || available == lastAvailable) {
                candidates.add(baths);
                lastAvailable = available;
                if (candidates.size() >= MAX_MACHINE_CHOICES) { break; }
            }
        }
        return candidates;
    }

    private Transition tryPlace(State state, int jobId, List<FjsspOperation> chain, int headBath) {
        int startTime = Math.max(state.jobAvailableTime()[jobId], state.bathAvailableTime()[headBath]);

        ChainPlacement placement = null;
        for (int attempt = 0; attempt <= chain.size(); attempt++) {
            ChainPlacement attemptPlacement = placeChain(state, jobId, chain, startTime, headBath);
            if (attemptPlacement.valid()) {
                placement = attemptPlacement;
                break;
            }
            if (attemptPlacement.blocked()) { return null; }
            startTime = attemptPlacement.requiredStart();
        }

        if (placement == null) {
            startTime = latestBathStart(state, jobId, chain, headBath);
            if (startTime == State.BLOCKED) { return null; }

            ChainPlacement fallback = placeChain(state, jobId, chain, startTime, headBath);
            if (!fallback.valid()) { return null; }
            placement = fallback;
        }

        return commit(state, jobId, chain, placement);
    }

    private int latestBathStart(State state, int jobId, List<FjsspOperation> chain, int headBath) {
        if (state.bathAvailableTime()[headBath] == State.BLOCKED) { return State.BLOCKED; }

        int startTime = state.jobAvailableTime()[jobId];
        startTime = Math.max(startTime, state.bathAvailableTime()[headBath]);

        for (int i = 1; i < chain.size(); i++) {
            int bathTime = earliestBathTime(state.bathAvailableTime(), chain.get(i).eligibleMachines());
            if (bathTime == State.BLOCKED) { return State.BLOCKED; }
            startTime = Math.max(startTime, bathTime);
        }
        return startTime;
    }

    private ChainPlacement placeChain(State state, int jobId, List<FjsspOperation> chain, int chainStart, int headBath) {
        int[] bathAvailableTime = state.bathAvailableTime().clone();
        int[] baths = new int[chain.size()];
        int[] startTimes = new int[chain.size()];
        int[] endTimes = new int[chain.size()];

        int jobReady = chainStart;

        for (int i = 0; i < chain.size(); i++) {
            FjsspOperation operation = chain.get(i);

            int bath;
            if (i == 0) {
                bath = headBath;
                if (bathAvailableTime[bath] == State.BLOCKED) { return ChainPlacement.blockedByCarrier(); }
            } else {
                bath = earliestFreeBath(bathAvailableTime, operation.eligibleMachines());
                if (bath == State.NO_BATH) { return ChainPlacement.blockedByCarrier(); }
            }

            int startTime = Math.max(jobReady, bathAvailableTime[bath]);

            if (i > 0) {
                FjsspOperation predecessor = chain.get(i - 1);
                int latestAdmissibleStart = startTimes[i - 1] + predecessor.maxDwellTime();
                if (startTime > latestAdmissibleStart) {
                    int shift = startTime - latestAdmissibleStart;
                    return ChainPlacement.invalid(chainStart + shift);
                }
            }

            baths[i] = bath;
            startTimes[i] = startTime;
            endTimes[i] = startTime + operation.processingOnOneMachine(bath);
            bathAvailableTime[bath] = endTimes[i];
            jobReady = endTimes[i];
        }
        return ChainPlacement.valid(baths, startTimes, endTimes);
    }

    private Transition commit(State state, int jobId, List<FjsspOperation> chain, ChainPlacement placement) {
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
            scheduledOperations.add(new ScheduledOperation(jobId, placement.baths()[i], placement.startTimes()[i], placement.endTimes()[i]));
        }

        newNextOperation[jobId] = firstIndex + chain.size();
        newJobAvailableTime[jobId] = placement.endTimes()[lastIndex];
        State newState = new State(newNextOperation, newBathAvailableTime, newJobAvailableTime, newJobBath);
        return new Transition(newState, scheduledOperations);
    }

    private int earliestFreeBath(int[] bathAvailableTime, List<Integer> eligible) {
        int best = State.NO_BATH;
        for (int bath : eligible) {
            if (bathAvailableTime[bath] == State.BLOCKED) { continue; }
            if (best == State.NO_BATH || bathAvailableTime[bath] < bathAvailableTime[best]) { best = bath; }
        }
        return best;
    }

    private int earliestBathTime(int[] bathAvailableTime, List<Integer> eligible) {
        int bath = earliestFreeBath(bathAvailableTime, eligible);
        if (bath == State.NO_BATH) { return State.BLOCKED; }
        return bathAvailableTime[bath];
    }

    private record ChainPlacement(boolean valid, boolean blocked, int requiredStart, int[] baths, int[] startTimes, int[] endTimes) {
        static ChainPlacement valid(int[] baths, int[] startTimes, int[] endTimes) {
            return new ChainPlacement(true, false, 0, baths, startTimes, endTimes);
        }

        static ChainPlacement invalid(int requiredStart) {
            return new ChainPlacement(false, false, requiredStart, null, null, null);
        }

        static ChainPlacement blockedByCarrier() {
            return new ChainPlacement(false, true, 0, null, null, null);
        }
    }

    public List<FjsspJob> getJobs() { return jobs; }
    public List<Machine> getMachines() { return baths; }
    public boolean isBlocking() { return blocking; }
    public int totalBaths() { return totalBaths; }
    public int stationCount() { return stationCount; }
    public int stationCapacity(int stationId) { return stationCapacity[stationId]; }
    public int stationOffset(int stationId) { return stationOffset[stationId]; }

    public int stationOfBath(int bathId) {
        for (int station = stationCount - 1; station >= 0; station--) {
            if (bathId >= stationOffset[station]) { return station; }
        }
        return 0;
    }
}
