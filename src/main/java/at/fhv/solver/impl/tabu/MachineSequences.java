package at.fhv.solver.impl.tabu;

import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Machine;
import at.fhv.model.jssp.Operation;
import at.fhv.model.jssp.Schedule;
import at.fhv.model.jssp.ScheduledOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MachineSequences {
    private final Map<Integer, List<Operation>> orderPerMachine;
    private final Map<Integer, Integer> capacities;
    private final Map<Operation, Integer> position;

    public MachineSequences(Map<Integer, List<Operation>> orderPerMachine, Map<Integer, Integer> capacities) {
        this.orderPerMachine = orderPerMachine;
        this.capacities = capacities;
        this.position = new HashMap<>();

        for (List<Operation> operations : orderPerMachine.values()) {
            for (int i = 0; i < operations.size(); i++) {
                position.put(operations.get(i), i);
            }
        }
    }

    public static MachineSequences fromSchedule(JsspProblem jsspProblem, Schedule schedule) {
        Map<Integer, List<ScheduledOperation>> perJob = new HashMap<>();
        for (ScheduledOperation scheduled : schedule.operations()) {
            perJob.computeIfAbsent(scheduled.jobId(), key -> new ArrayList<>()).add(scheduled);
        }

        Map<Integer, List<Placed>> placed = new HashMap<>();
        Map<Integer, Integer> capacities = new HashMap<>();
        for (Machine machine : jsspProblem.getMachines()) {
            placed.put(machine.machineId(), new ArrayList<>());
            capacities.put(machine.machineId(), machine.capacity());
        }

        for (Job job : jsspProblem.getJobs()) {
            List<ScheduledOperation> scheduledOperations = perJob.get(job.jobId());
            if (scheduledOperations == null || scheduledOperations.size() != job.operations().size()) {
                throw new IllegalArgumentException("Timeplan does not fit to job " + job.jobId());
            }

            scheduledOperations.sort(Comparator.comparingInt(ScheduledOperation::startTime));

            for (int i = 0; i < job.operations().size(); i++) {
                Operation operation = job.operations().get(i);
                placed.get(operation.machineId()).add(new Placed(operation, scheduledOperations.get(i).startTime()));
            }
        }

        Map<Integer, List<Operation>> order = new HashMap<>();
        for (Map.Entry<Integer, List<Placed>> entry : placed.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(Placed::startTime));

            List<Operation> operations = new ArrayList<>();
            for (Placed placedOperation : entry.getValue()) {
                operations.add(placedOperation.operation());
            }
            order.put(entry.getKey(), operations);
        }
        return new MachineSequences(order, capacities);
    }

    private record Placed(Operation operation, int startTime) {}

    public Map<Integer, List<Operation>> orderPerMachine() {
        return Collections.unmodifiableMap(orderPerMachine);
    }

    public int capacityOf(int machineId) {
        Integer capacity = capacities.get(machineId);
        return capacity == null ? 1 : capacity;
    }

    public Operation machinePredecessor(Operation operation) {
        if (operation == null) { return null; }

        Integer i = position.get(operation);
        if (i == null) { return null; }

        int stride = capacityOf(operation.machineId());
        if (i - stride < 0) { return null; }

        return orderPerMachine.get(operation.machineId()).get(i - stride);
    }

    public Operation machineSuccessor(Operation operation) {
        if (operation == null) { return null; }

        Integer i = position.get(operation);
        if (i == null) { return null; }

        List<Operation> operations = orderPerMachine.get(operation.machineId());
        int stride = capacityOf(operation.machineId());
        if (i + stride >= operations.size()) { return null; }

        return operations.get(i + stride);
    }

    public MachineSequences applied(Move move) {
        Map<Integer, List<Operation>> newOrderPerMachine = deepCopyOrder();
        List<Operation> operations = newOrderPerMachine.get(move.machineId());

        if (operations == null) {
            throw new IllegalArgumentException("Unknown machine in move: " + move.machineId());
        }

        Integer indexA = position.get(move.operationA());
        Integer indexB = position.get(move.operationB());

        if (indexA == null || indexB == null || indexA.equals(indexB)) {
            throw new IllegalArgumentException("Invalid move: " + move.operationA() + " <-> " + move.operationB());
        }

        switch (move.type()) {
            case SWAP:
                Operation temp = operations.get(indexA);
                operations.set(indexA, operations.get(indexB));
                operations.set(indexB, temp);
                break;
            case MOVE_AFTER:
                reinsert(operations, indexA, indexB, true);
                break;
            case MOVE_BEFORE:
                reinsert(operations, indexA, indexB, false);
                break;
            default:
                throw new IllegalArgumentException("Unknown move type: " + move.type());
        }

        return new MachineSequences(newOrderPerMachine, capacities);
    }

    private void reinsert(List<Operation> operations, int indexA, int indexB, boolean after) {
        Operation moved = operations.remove(indexA);

        int target = indexB;
        if (indexA < indexB) { target = indexB - 1; }
        if (after) { target = target + 1; }

        operations.add(target, moved);
    }

    @Deprecated
    public MachineSequences swapped(Move move) {
        return applied(move);
    }

    public MachineSequences copy() {
        return new MachineSequences(deepCopyOrder(), capacities);
    }

    private Map<Integer, List<Operation>> deepCopyOrder() {
        Map<Integer, List<Operation>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<Operation>> entry : orderPerMachine.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }
}