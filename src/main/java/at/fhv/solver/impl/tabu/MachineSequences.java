package at.fhv.solver.impl.tabu;

import at.fhv.model.jssp.Operation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MachineSequences {
    private final Map<Integer, List<Operation>> orderPerMachine;

    public MachineSequences(Map<Integer, List<Operation>> orderPerMachine) {
        this.orderPerMachine = orderPerMachine;
    }

    public Operation machinePredecessor(Operation operation) {
        if (operation == null) {return null;}
        List<Operation> operations = orderPerMachine.get(operation.machineId());
        if (operations == null) {return null;}

        int i = operations.indexOf(operation);
        if (i <= 0) {return null;}

        return operations.get(i - 1);
    }

    public Operation machineSuccessor(Operation operation) {
        if (operation == null) {return null;}
        List<Operation> operations = orderPerMachine.get(operation.machineId());
        if (operations == null) {return null;}

        int i = operations.indexOf(operation);
        if (i == operations.size() - 1 || i == -1 ) {return null;}

        return operations.get(i + 1);
    }

    public MachineSequences swapped(Move move) {
        Map<Integer, List<Operation>> newOrderPerMachine = new HashMap<>();

        for (Map.Entry<Integer, List<Operation>> entry : orderPerMachine.entrySet()) {
            List<Operation> operations = new ArrayList<>(entry.getValue());
            newOrderPerMachine.put(entry.getKey(), operations);
        }

        List<Operation> operations = newOrderPerMachine.get(move.machineId());

        int iA = operations.indexOf(move.operationA());
        int iB = operations.indexOf(move.operationB());

        Operation temp = operations.get(iA);
        operations.set(iA, operations.get(iB));
        operations.set(iB, temp);
        return new MachineSequences(newOrderPerMachine);
    }

    public MachineSequences copy() {
        return new MachineSequences(orderPerMachine);
    }
}
