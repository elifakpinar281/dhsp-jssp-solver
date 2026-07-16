package at.fhv.solver.impl.tabu;

import at.fhv.model.jssp.Operation;

import java.util.ArrayList;
import java.util.List;

public class CriticalBlockFinder {
    private CriticalBlockFinder() {}

    public static List<List<Operation>> of(List<Operation> criticalPath, MachineSequences machineSequences) {
        List<List<Operation>> blocks = new ArrayList<>();
        List<Operation> currentBlock = new ArrayList<>();

        for (Operation operation : criticalPath) {
            if (currentBlock.isEmpty()) {
                currentBlock.add(operation);
                continue;
            }

            Operation last = currentBlock.get(currentBlock.size() - 1);
            boolean machineAdjacent = operation.equals(machineSequences.machineSuccessor(last));

            if (machineAdjacent) {
                currentBlock.add(operation);
            } else {
                if (currentBlock.size() >= 2) { blocks.add(currentBlock); }
                currentBlock = new ArrayList<>();
                currentBlock.add(operation);
            }
        }
        if (currentBlock.size() >= 2) { blocks.add(currentBlock); }
        return blocks;

    }
}
