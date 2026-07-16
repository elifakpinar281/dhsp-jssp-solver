package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.Operation;
import at.fhv.solver.impl.tabu.*;

import java.util.ArrayList;
import java.util.List;

public class N5Neighbourhood implements INeighbourhood {
    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences machineSequences) {
        List<Move> moves = new ArrayList<>();
        List<List<Operation>> blocks = CriticalBlockFinder.of(evaluationResult.criticalPath(), machineSequences);

        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            List<Operation> block = blocks.get(blockIndex);
            int size = block.size();

            boolean isFirstBlock = blockIndex == 0;
            boolean isLastBlock = blockIndex == blocks.size() - 1;

            if (size == 2) {
                moves.add(swap(block, 0));
                continue;
            }

            if (!isFirstBlock) {
                moves.add(swap(block, 0));
            }
            if (!isLastBlock) {
                moves.add(swap(block, size - 2));
            }
        }

        if (moves.isEmpty()) {
            for (List<Operation> block : blocks) {
                for (int i = 0; i + 1 < block.size(); i++) {
                    moves.add(swap(block, i));
                }
            }
        }
        return moves;
    }

    private Move swap(List<Operation> block, int index) {
        Operation left = block.get(index);
        Operation right = block.get(index + 1);
        return new Move(left, right, left.machineId());
    }
}