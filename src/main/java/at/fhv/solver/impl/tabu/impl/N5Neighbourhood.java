package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.Operation;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.MachineSequences;
import at.fhv.solver.impl.tabu.Move;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;

import java.util.ArrayList;
import java.util.List;

public class N5Neighbourhood implements INeighbourhood {

    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences machineSequences) {
        List<Move> moves = new ArrayList<>();
        List<List<Operation>> blocks = breakIntoBlocks(evaluationResult.criticalPath(), machineSequences);

        for (List<Operation> block : blocks) {
            int n = block.size();
            for (int i = 0; i + 1 < n; i++) {
                moves.add(new Move(block.get(i), block.get(i + 1), block.get(i).machineId()));
            }
        }

        return moves;
    }

    private List<List<Operation>> breakIntoBlocks(List<Operation> criticalPath, MachineSequences sequences) {
        List<List<Operation>> blocks = new ArrayList<>();
        List<Operation> currentBlock = new ArrayList<>();

        for (Operation operation : criticalPath) {
            if (currentBlock.isEmpty()) {
                currentBlock.add(operation);
                continue;
            }

            Operation last = currentBlock.get(currentBlock.size() - 1);
            boolean machineAdjacent = operation.equals(sequences.machineSuccessor(last));

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