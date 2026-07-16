package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.Operation;
import at.fhv.solver.impl.tabu.*;

import java.util.ArrayList;
import java.util.List;

public class N1Neighbourhood implements INeighbourhood {
    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences machineSequences) {
        List<Move> moves = new ArrayList<>();
        List<List<Operation>> blocks = CriticalBlockFinder.of(evaluationResult.criticalPath(), machineSequences);

        for (List<Operation> block : blocks) {
            for (int i = 0; i + 1 < block.size(); i++) {
                Operation left = block.get(i);
                Operation right = block.get(i + 1);
                moves.add(new Move(left, right, left.machineId()));
            }
        }
        return moves;
    }
}