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
            List<Operation> criticalPath,
            Map<Operation, Integer> head,
            Map<Operation, Integer> tail,
            List<DwellViolation> dwellViolations
    ) {
        public boolean dwellFeasible() {
            return dwellViolations.isEmpty();
        }

        public int violationCount() {
            return dwellViolations.size();
        }

        public int violationPenalty() {
            int penalty = 0;
            for (DwellViolation violation : dwellViolations) {
                penalty += violation.excess();
            }
            return penalty;
        }
    }

    public record DwellViolation(Operation operation, int dwell, int maxDwell, int excess) {}

    public EvaluationResult evaluate(MachineSequences sequences) {
        if (operationCount == 0) {
            return new EvaluationResult(0, new ArrayList<>(), new HashMap<>(), new HashMap<>(), new ArrayList<>());
        }

        int[] machinePredecessor = new int[operationCount];
        int[] machineSuccessor = new int[operationCount];
        buildMachineLinks(sequences, machinePredecessor, machineSuccessor);

        int[] start = solveWithTimeLags(machinePredecessor, machineSuccessor);
        List<DwellViolation> violations;

        if (start != null) { violations = new ArrayList<>();
        } else {
            start = earliestStart(machinePredecessor, machineSuccessor);
            violations = findDwellViolations(start);
            if (violations.isEmpty()) { violations.add(new DwellViolation(operations.get(0), 0, 0, 1)); }
        }

        int makespan = 0;
        for (int i = 0; i < operationCount; i++) {
            makespan = Math.max(makespan, start[i] + processingTime[i]);
        }

        int[] tail = computeTail(machinePredecessor, machineSuccessor);
        List<Operation> criticalPath = criticalPath(start, machinePredecessor, makespan);
        return new EvaluationResult(makespan, criticalPath, toMap(start), toMap(tail), violations);
    }

    private void buildMachineLinks(MachineSequences sequences, int[] machinePredecessor, int[] machineSuccessor) {
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

    private int[] solveWithTimeLags(int[] machinePredecessor, int[] machineSuccessor) {
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

        int[] targets = new int[3];
        int[] weights = new int[3];

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

    private int[] earliestStart(int[] machinePredecessor, int[] machineSuccessor) {
        int[] start = new int[operationCount];

        for (int index : topologicalOrder(machinePredecessor, machineSuccessor)) {
            int jobEnd = 0;
            int machineEnd = 0;

            int predecessor = jobPredecessor[index];
            if (predecessor != NONE) { jobEnd = start[predecessor] + processingTime[predecessor]; }

            int machine = machinePredecessor[index];
            if (machine != NONE) { machineEnd = start[machine] + processingTime[machine]; }

            start[index] = Math.max(jobEnd, machineEnd);
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

    private List<Operation> criticalPath(int[] start, int[] machinePredecessor, int makespan) {
        int current = NONE;
        for (int i = 0; i < operationCount; i++) {
            if (start[i] + processingTime[i] == makespan) {
                current = i;
                break;
            }
        }

        List<Operation> path = new ArrayList<>();
        while (current != NONE) {
            path.add(operations.get(current));
            int previous = NONE;

            int predecessor = jobPredecessor[current];
            if (predecessor != NONE && start[predecessor] + processingTime[predecessor] == start[current]) {
                previous = predecessor;
            }

            if (previous == NONE) {
                int machine = machinePredecessor[current];
                if (machine != NONE && start[machine] + processingTime[machine] == start[current]) {
                    previous = machine;
                }
            }

            current = previous;
        }

        Collections.reverse(path);
        return path;
    }

    private List<DwellViolation> findDwellViolations(int[] start) {
        List<DwellViolation> violations = new ArrayList<>();

        for (int i = 0; i < operationCount; i++) {
            if (maxDwellTime[i] == Operation.NO_LIMIT) { continue; }

            int successor = jobSuccessor[i];
            if (successor == NONE) { continue; }

            int dwell = start[successor] - start[i];
            if (dwell > maxDwellTime[i]) {
                violations.add(new DwellViolation(operations.get(i), dwell, maxDwellTime[i], dwell - maxDwellTime[i]));
            }
        }

        return violations;
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