package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.Operation;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.MachineSequences;
import at.fhv.solver.impl.tabu.Move;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;

import java.util.*;

public class AdjacentSwapNeighbourhood implements INeighbourhood {
    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences machineSequences) {
        Set<Integer> criticalMachines = new HashSet<>();
        for (Operation operation : evaluationResult.criticalPath()) {
            criticalMachines.add(operation.machineId());
        }

        List<Move> moves = adjacentSwaps(machineSequences, criticalMachines);
        if (moves.isEmpty()) {
            moves = adjacentSwaps(machineSequences, null);
        }
        return moves;
    }

    private List<Move> adjacentSwaps(MachineSequences machineSequences, Set<Integer> onlyMachines) {
        List<Move> moves = new ArrayList<>();

        for (Map.Entry<Integer, List<Operation>> entry : machineSequences.orderPerMachine().entrySet()) {
            if (onlyMachines != null && !onlyMachines.contains(entry.getKey())) { continue; }

            List<Operation> operations = entry.getValue();
            for (int i = 0; i + 1 < operations.size(); i++) {
                moves.add(new Move(operations.get(i), operations.get(i + 1), entry.getKey()));
            }
        }
        return moves;
    }
}