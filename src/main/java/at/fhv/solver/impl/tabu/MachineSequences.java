package at.fhv.solver.impl.tabu;

import at.fhv.model.jssp.Operation;

import java.util.ArrayList;
import java.util.Collections;
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

    public MachineSequences swapped(Move move) {
        Map<Integer, List<Operation>> newOrderPerMachine = deepCopyOrder();

        List<Operation> operations = newOrderPerMachine.get(move.machineId());
        if (operations == null) { return new MachineSequences(newOrderPerMachine, capacities); }

        Integer iA = position.get(move.operationA());
        Integer iB = position.get(move.operationB());
        if (iA == null || iB == null || iA.equals(iB)) { return new MachineSequences(newOrderPerMachine, capacities); }

        Operation temp = operations.get(iA);
        operations.set(iA, operations.get(iB));
        operations.set(iB, temp);

        return new MachineSequences(newOrderPerMachine, capacities);
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