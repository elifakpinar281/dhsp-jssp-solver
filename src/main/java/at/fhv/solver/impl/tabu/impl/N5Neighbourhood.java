package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.Operation;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.Move;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;

import java.util.ArrayList;
import java.util.List;

public class N5Neighbourhood implements INeighbourhood {

    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult) {
        List<Move> moves = new ArrayList<>();
        List<List<Operation>> blocks = breakIntoBlocks(evaluationResult.criticalPath());

        for (List<Operation> block : blocks) {
            int n = block.size();
            addMove(moves, block.get(0), block.get(1), block.get(0).machineId());

            if (n >= 3) {
                addMove(moves, block.get(n-2), block.get(n-1), block.get(n-1).machineId());
            }
        }

        return moves;
    }

    private List<List<Operation>> breakIntoBlocks(List<Operation> criticalPath) {
        List<List<Operation>> blocks = new ArrayList<>();
        List<Operation> currentBlock = new ArrayList<>();

        for (Operation operation : criticalPath) {
            if (currentBlock.isEmpty() || operation.machineId() == currentBlock.get(currentBlock.size() - 1).machineId()) {
                currentBlock.add(operation);
            } else {
                if (currentBlock.size() >= 2) {
                    blocks.add(currentBlock);
                }

                currentBlock = new ArrayList<>();
                currentBlock.add(operation);
            }
        }

        if (currentBlock.size() >= 2) {
            blocks.add(currentBlock);
        }

        return blocks;
    }

    private void addMove(List<Move> moves, Operation a, Operation b, int machineId) {
        moves.add(new Move(a, b, machineId));
    }
}
