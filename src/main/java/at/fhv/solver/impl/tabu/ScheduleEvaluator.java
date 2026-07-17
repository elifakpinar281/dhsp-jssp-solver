package at.fhv.solver.impl.tabu;

import at.fhv.model.jssp.*;

import java.util.*;

public class ScheduleEvaluator {
    private static final int NONE = -1;
    private final JsspProblem jsspProblem;

    private final List<Operation> operations;
    private final Map<Operation, Integer> indexOf;
    private final int operationCount;

    private final int[] jobOffset;
    private final boolean fastIndexUsable;
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

        this.jobOffset = new int[jsspProblem.getJobs().size()];
        boolean usable = true;
        int offset = 0;
        for (Job job : jsspProblem.getJobs()) {
            if (job.jobId() < 0 || job.jobId() >= jobOffset.length) { usable = false; break; }
            jobOffset[job.jobId()] = offset;
            for (int i = 0; i < job.operations().size(); i++) {
                if (job.operations().get(i).operationId() != i) { usable = false; }
            }
            offset = offset + job.operations().size();
        }
        this.fastIndexUsable = usable;

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
            Map<Operation, Integer> head
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
            return new EvaluationResult(0, true, new ArrayList<>(), new HashMap<>());
        }

        int[] machinePredecessor = new int[operationCount];
        int[] machineSuccessor = new int[operationCount];
        buildLinks(sequences, machinePredecessor, machineSuccessor);

        int[] start = solveWithLags(machinePredecessor, machineSuccessor);

        if (start == null) {
            return new EvaluationResult(Integer.MAX_VALUE, false, new ArrayList<>(), new HashMap<>());
        }

        int makespan = 0;
        for (int i = 0; i < operationCount; i++) {
            makespan = Math.max(makespan, start[i] + processingTime[i]);
        }

        List<Operation> criticalPath = criticalPath(start, machinePredecessor, machineSuccessor, makespan);
        return new EvaluationResult(makespan, true, criticalPath, toMap(start));
    }

    public Map<Operation, Integer> slack(MachineSequences sequences) {
        Map<Operation, Integer> result = new HashMap<>();
        if (operationCount == 0) { return result; }

        int[] machinePredecessor = new int[operationCount];
        int[] machineSuccessor = new int[operationCount];
        buildLinks(sequences, machinePredecessor, machineSuccessor);

        int[] start = solveWithLags(machinePredecessor, machineSuccessor);
        if (start == null) { return result; }

        int makespan = 0;
        for (int i = 0; i < operationCount; i++) {
            makespan = Math.max(makespan, start[i] + processingTime[i]);
        }

        List<int[]> arcs = buildArcs(machineSuccessor);

        int[] latest = new int[operationCount];
        for (int i = 0; i < operationCount; i++) {
            latest[i] = makespan - processingTime[i];
        }

        for (int round = 0; round <= operationCount; round++) {
            boolean changed = false;
            for (int[] arc : arcs) {
                int candidate = latest[arc[1]] - arc[2];
                if (candidate < latest[arc[0]]) {
                    latest[arc[0]] = candidate;
                    changed = true;
                }
            }
            if (!changed) { break; }
        }

        for (int i = 0; i < operationCount; i++) {
            result.put(operations.get(i), latest[i] - start[i]);
        }
        return result;
    }

    private List<int[]> buildArcs(int[] machineSuccessor) {
        List<int[]> arcs = new ArrayList<>();

        for (int current = 0; current < operationCount; current++) {
            if (jobSuccessor[current] != NONE) {
                arcs.add(new int[]{current, jobSuccessor[current], processingTime[current]});
            }
            if (machineSuccessor[current] != NONE) {
                arcs.add(new int[]{current, machineSuccessor[current], processingTime[current]});
            }

            int predecessor = jobPredecessor[current];
            if (predecessor != NONE && maxDwellTime[predecessor] != Operation.NO_LIMIT) {
                arcs.add(new int[]{current, predecessor, -maxDwellTime[predecessor]});
            }
            if (jsspProblem.isBlocking() && predecessor != NONE && machineSuccessor[predecessor] != NONE) {
                arcs.add(new int[]{current, machineSuccessor[predecessor], 0});
            }
        }
        return arcs;
    }

    private int index(Operation operation) {
        if (fastIndexUsable) { return jobOffset[operation.jobId()] + operation.operationId(); }
        return indexOf.get(operation);
    }

    private void buildLinks(MachineSequences sequences, int[] machinePredecessor, int[] machineSuccessor) {
        Arrays.fill(machinePredecessor, NONE);
        Arrays.fill(machineSuccessor, NONE);

        for (Map.Entry<Integer, List<Operation>> entry : sequences.orderPerMachine().entrySet()) {
            List<Operation> machineOperations = entry.getValue();
            int stride = jsspProblem.capacityOf(entry.getKey());

            for (int i = 0; i < machineOperations.size(); i++) {
                int index = index(machineOperations.get(i));
                if (i - stride >= 0) { machinePredecessor[index] = index(machineOperations.get(i - stride)); }
                if (i + stride < machineOperations.size()) { machineSuccessor[index] = index(machineOperations.get(i + stride)); }
            }
        }
    }

    private int[] solveWithLags(int[] machinePredecessor, int[] machineSuccessor) {
        int n = operationCount;
        int[] start = new int[n];
        int[] pathEdges = new int[n];
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
                pathEdges[target] = pathEdges[current] + 1;
                if (pathEdges[target] >= n) { return null; }

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