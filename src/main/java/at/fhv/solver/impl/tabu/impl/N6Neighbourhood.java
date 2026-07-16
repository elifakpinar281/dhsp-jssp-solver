package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.Operation;
import at.fhv.solver.impl.tabu.*;

import java.util.ArrayList;
import java.util.List;

public class N6Neighbourhood implements INeighbourhood {

    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences machineSequences) {
        List<Move> moves = new ArrayList<>();
        List<List<Operation>> blocks = CriticalBlockFinder.of(evaluationResult.criticalPath(), machineSequences);

        for (List<Operation> block : blocks) {
            int size = block.size();

            if (size == 2) {
                Operation left = block.get(0);
                moves.add(new Move(left, block.get(1), left.machineId()));
                continue;
            }

            Operation first = block.get(0);
            Operation last = block.get(size - 1);

            for (int i = 0; i < size - 1; i++) {
                Operation operation = block.get(i);
                moves.add(new Move(operation, last, operation.machineId(), Move.MoveType.MOVE_AFTER));
            }

            for (int i = 1; i < size; i++) {
                Operation operation = block.get(i);
                moves.add(new Move(operation, first, operation.machineId(), Move.MoveType.MOVE_BEFORE));
            }
        }
        return moves;
    }
}
