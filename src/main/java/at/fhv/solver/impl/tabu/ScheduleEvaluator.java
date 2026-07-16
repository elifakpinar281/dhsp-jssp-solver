package at.fhv.solver.impl.tabu;

import at.fhv.model.jssp.*;

import java.util.*;

public class ScheduleEvaluator {
    private static final int NONE = -1;
    private final JsspProblem jsspProblem;

    private final List<Operation> operations;
    private final Map<Operation, Integer> indexOf;
    private final int operationCount;

    private final int[] processingTime;
    private final int[] maxDwellTime;
    private final int[] jobPredecessor;
    private final int[] jobSuccessor;

    public ScheduleEvaluator(JsspProblem jsspProblem) {
        this.jsspProblem = jsspProblem;

        this.operations = new ArrayList<>();
        for (Job job : jsspProblem.getJobs()) {
            operations.addAll(job.operations());
        }

        this.operationCount = operations.size();
        this.indexOf = new HashMap<>();
        for (int i = 0; i < operationCount; i++) {
            indexOf.put(operations.get(i), i);
        }

        this.processingTime = new int[operationCount];
        this.maxDwellTime = new int[operationCount];
        this.jobPredecessor = new int[operationCount];
        this.jobSuccessor = new int[operationCount];
        Arrays.fill(jobPredecessor, NONE);
        Arrays.fill(jobSuccessor, NONE);

        for (Job job : jsspProblem.getJobs()) {
            List<Operation> jobOperations = job.operations();
            for (int i = 0; i < jobOperations.size(); i++) {
                int index = indexOf.get(jobOperations.get(i));
                processingTime[index] = jobOperations.get(i).processingTime();
                maxDwellTime[index] = jobOperations.get(i).maxDwellTime();
                if (i > 0) { jobPredecessor[index] = indexOf.get(jobOperations.get(i - 1)); }
                if (i < jobOperations.size() - 1) { jobSuccessor[index] = indexOf.get(jobOperations.get(i + 1)); }
            }
        }
    }

    public record EvaluationResult(
            int makespan,
            boolean valid,
            List<Operation> criticalPath,
            Map<Operation, Integer> head,
            Map<Operation, Integer> tail
    ) {
        public boolean dwellValid() { return valid; }
    }

    public record Result(boolean valid, int makespan) {}

    public Result evaluateResult (MachineSequences sequences) {
        if (operationCount == 0) { return new Result (true, 0); }

        int[] machinePredecessor = new int[operationCount];
        int[] machineSuccessor = new int[operationCount];
        buildLinks(sequences, machinePredecessor, machineSuccessor);

        int[] start = solveWithLags(machinePredecessor, machineSuccessor);
        if (start == null) { return new Result(false, Integer.MAX_VALUE); }

        int makespan = 0;
        for (int i = 0; i < operationCount; i++) {
            makespan = Math.max(makespan, start[i] + processingTime[i]);
        }
        return new Result(true, makespan);
    }

    public EvaluationResult evaluate(MachineSequences sequences) {
        if (operationCount == 0) {
            return new EvaluationResult(0, true, new ArrayList<>(), new HashMap<>(), new HashMap<>());
        }

        int[] machinePredecessor = new int[operationCount];
        int[] machineSuccessor = new int[operationCount];
        buildLinks(sequences, machinePredecessor, machineSuccessor);

        int[] start = solveWithLags(machinePredecessor, machineSuccessor);

        if (start == null) {
            return new EvaluationResult(Integer.MAX_VALUE, false, new ArrayList<>(), new HashMap<>(), new HashMap<>());
        }

        int makespan = 0;
        for (int i = 0; i < operationCount; i++) {
            makespan = Math.max(makespan, start[i] + processingTime[i]);
        }

        int[] tail = computeTail(machinePredecessor, machineSuccessor);
        List<Operation> criticalPath = criticalPath(start, machinePredecessor, machineSuccessor, makespan);
        return new EvaluationResult(makespan, true, criticalPath, toMap(start), toMap(tail));
    }

    private void buildLinks(MachineSequences sequences, int[] machinePredecessor, int[] machineSuccessor) {
        Arrays.fill(machinePredecessor, NONE);
        Arrays.fill(machineSuccessor, NONE);

        for (Map.Entry<Integer, List<Operation>> entry : sequences.orderPerMachine().entrySet()) {
            List<Operation> machineOperations = entry.getValue();
            int stride = jsspProblem.capacityOf(entry.getKey());

            for (int i = 0; i < machineOperations.size(); i++) {
                int index = indexOf.get(machineOperations.get(i));
                if (i - stride >= 0) { machinePredecessor[index] = indexOf.get(machineOperations.get(i - stride)); }
                if (i + stride < machineOperations.size()) { machineSuccessor[index] = indexOf.get(machineOperations.get(i + stride)); }
            }
        }
    }

    private int[] solveWithLags(int[] machinePredecessor, int[] machineSuccessor) {
        int n = operationCount;
        int[] start = new int[n];
        int[] relaxCount = new int[n];
        boolean[] inQueue = new boolean[n];

        int[] queue = new int[n + 1];
        int queueHead = 0;
        int queueTail = 0;
        int queueSize = 0;

        for (int i = 0; i < n; i++) {
            queue[queueTail] = i;
            queueTail = (queueTail + 1) % (n + 1);
            queueSize++;
            inQueue[i] = true;
        }

        int[] targets = new int[4];
        int[] weights = new int[4];

        while (queueSize > 0) {
            int current = queue[queueHead];
            queueHead = (queueHead + 1) % (n + 1);
            queueSize--;
            inQueue[current] = false;

            int arcCount = 0;

            if (jobSuccessor[current] != NONE) {
                targets[arcCount] = jobSuccessor[current];
                weights[arcCount] = processingTime[current];
                arcCount++;
            }

            if (machineSuccessor[current] != NONE) {
                targets[arcCount] = machineSuccessor[current];
                weights[arcCount] = processingTime[current];
                arcCount++;
            }

            int predecessor = jobPredecessor[current];
            if (predecessor != NONE && maxDwellTime[predecessor] != Operation.NO_LIMIT) {
                targets[arcCount] = predecessor;
                weights[arcCount] = -maxDwellTime[predecessor];
                arcCount++;
            }

            if (jsspProblem.isBlocking() && predecessor != NONE && machineSuccessor[predecessor] != NONE) {
                targets[arcCount] = machineSuccessor[predecessor];
                weights[arcCount] = 0;
                arcCount++;
            }

            for (int k = 0; k < arcCount; k++) {
                int target = targets[k];
                int candidate = start[current] + weights[k];
                if (candidate <= start[target]) { continue; }

                start[target] = candidate;
                relaxCount[target]++;
                if (relaxCount[target] > n) { return null; }

                if (!inQueue[target]) {
                    queue[queueTail] = target;
                    queueTail = (queueTail + 1) % (n + 1);
                    queueSize++;
                    inQueue[target] = true;
                }
            }
        }
        return start;
    }

    private int[] computeTail(int[] machinePredecessor, int[] machineSuccessor) {
        int[] tail = new int[operationCount];
        int[] order = topologicalOrder(machinePredecessor, machineSuccessor);

        for (int i = order.length - 1; i >= 0; i--) {
            int index = order[i];
            int jobTail = 0;
            int machineTail = 0;

            int successor = jobSuccessor[index];
            if (successor != NONE) { jobTail = tail[successor] + processingTime[successor]; }

            int machine = machineSuccessor[index];
            if (machine != NONE) { machineTail = tail[machine] + processingTime[machine]; }

            tail[index] = Math.max(jobTail, machineTail);
        }
        return tail;
    }

    private int[] topologicalOrder(int[] machinePredecessor, int[] machineSuccessor) {
        int n = operationCount;
        int[] inDegree = new int[n];

        for (int i = 0; i < n; i++) {
            int degree = 0;
            if (jobPredecessor[i] != NONE) { degree++; }
            if (machinePredecessor[i] != NONE) { degree++; }
            inDegree[i] = degree;
        }

        int[] queue = new int[n];
        int queueTail = 0;
        for (int i = 0; i < n; i++) {
            if (inDegree[i] == 0) { queue[queueTail++] = i; }
        }

        int[] result = new int[n];
        int resultSize = 0;
        int queueHead = 0;

        while (queueHead < queueTail) {
            int current = queue[queueHead++];
            result[resultSize++] = current;

            int successor = jobSuccessor[current];
            if (successor != NONE && --inDegree[successor] == 0) { queue[queueTail++] = successor; }

            int machine = machineSuccessor[current];
            if (machine != NONE && --inDegree[machine] == 0) { queue[queueTail++] = machine; }
        }

        if (resultSize != n) { throw new IllegalStateException("Cycle detected"); }
        return result;
    }

    private List<Operation> criticalPath(int[] start, int[] machinePredecessor, int[] machineSuccessor, int makespan) {
        int current = NONE;
        for(int i = 0; i < operationCount; i++) {
            if (start[i] + processingTime[i] == makespan) {
                current = i;
                break;
            }
        }
        List<List<Integer>> blocking = buildBlocking(machineSuccessor);
        List<Operation> path = new ArrayList<>();
        boolean[] visited = new boolean[operationCount];

        while (current != NONE && !visited[current]) {
            path.add(operations.get(current));
            visited[current] = true;
            current = closePredecessor(current, start, machinePredecessor, blocking, visited);
        }
        Collections.reverse(path);
        return path;
    }

    private List<List<Integer>> buildBlocking(int[] machineSuccessor) {
        List<List<Integer>> sources = new ArrayList<>();
        for (int i = 0; i < operationCount; i++) {
            sources.add(new ArrayList<>());
        }

        if (!jsspProblem.isBlocking()) { return sources; }

        for (int j = 0; j < operationCount; j++) {
            int predecessor = jobPredecessor[j];
            if (predecessor == NONE) { continue; }

            int target = machineSuccessor[predecessor];
            if (target == NONE) { continue; }

            sources.get(target).add(j);
        }
        return sources;
    }

    private int closePredecessor(int current, int[] start, int[] machinePredecessor, List<List<Integer>> blocking, boolean[] visited) {
        int predecessor = jobPredecessor[current];
        if (predecessor != NONE && isClose(predecessor, processingTime[predecessor], current, start, visited)) { return predecessor; }
        int machine = machinePredecessor[current];
        if (machine != NONE && isClose(machine, processingTime[machine], current, start, visited)) { return machine; }

        for (int source : blocking.get(current)) {
            if (isClose(source, 0, current, start, visited)) { return source; }
        }
        int successor = jobSuccessor[current];
        if (successor != NONE && maxDwellTime[current] != Operation.NO_LIMIT && isClose(successor, -maxDwellTime[current], current, start, visited)) {
            return successor;
        }

        return NONE;
    }

    private boolean isClose(int source, int weight, int target, int[] start, boolean[] visited) {
        if (source == NONE || visited[source]) { return false; }
        return start[source] + weight == start[target];
    }

    private Map<Operation, Integer> toMap(int[] values) {
        Map<Operation, Integer> map = new HashMap<>();
        for (int i = 0; i < operationCount; i++) {
            map.put(operations.get(i), values[i]);
        }
        return map;
    }

    public Schedule toSchedule(EvaluationResult result) {
        List<ScheduledOperation> scheduledOperations = new ArrayList<>();
        for (Job job : jsspProblem.getJobs()) {
            for (Operation operation : job.operations()) {
                int start = result.head().get(operation);
                int end = start + operation.processingTime();
                scheduledOperations.add(new ScheduledOperation(operation.jobId(), operation.machineId(), start, end));
            }
        }

        return new Schedule(scheduledOperations);
    }
}