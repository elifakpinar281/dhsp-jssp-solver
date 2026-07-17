package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.Operation;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.MachineSequences;
import at.fhv.solver.impl.tabu.Move;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;

import java.util.*;

public class StrideNeighbourhood implements INeighbourhood {
    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences machineSequences) {
        List<Move> moves = new ArrayList<>();
        Set<Move.MoveAttribute> seen = new HashSet<>();

        for (Operation operation : evaluationResult.criticalPath()) {
            addStrideMoves(operation, machineSequences, moves, seen);
            addAdjacentMoves(operation, machineSequences, moves, seen);
        }

        if (moves.isEmpty()) {
            return allAdjacentSwaps(machineSequences);
        }
        return moves;
    }

    private void addStrideMoves(Operation operation, MachineSequences machineSequences, List<Move> moves, Set<Move.MoveAttribute> seen) {
        Operation successor = machineSequences.machineSuccessor(operation);
        if (successor != null) {
            add(new Move(operation, successor, operation.machineId()), moves, seen);
        }

        Operation predecessor = machineSequences.machinePredecessor(operation);
        if (predecessor != null) {
            add(new Move(predecessor, operation, operation.machineId()), moves, seen);
        }
    }

    private void addAdjacentMoves(Operation operation, MachineSequences machineSequences, List<Move> moves, Set<Move.MoveAttribute> seen) {
        int index = machineSequences.positionOf(operation);
        if (index < 0) { return; }

        Operation next = machineSequences.at(operation.machineId(), index + 1);
        if (next != null) {
            add(new Move(operation, next, operation.machineId()), moves, seen);
        }

        Operation previous = machineSequences.at(operation.machineId(), index - 1);
        if (previous != null) {
            add(new Move(previous, operation, operation.machineId()), moves, seen);
        }
    }

    private void add(Move move, List<Move> moves, Set<Move.MoveAttribute> seen) {
        if (seen.add(move.getAttribute())) {
            moves.add(move);
        }
    }

    private List<Move> allAdjacentSwaps(MachineSequences machineSequences) {
        List<Move> moves = new ArrayList<>();
        for (Map.Entry<Integer, List<Operation>> entry : machineSequences.orderPerMachine().entrySet()) {
            List<Operation> operations = entry.getValue();
            for (int i = 0; i + 1 < operations.size(); i++) {
                moves.add(new Move(operations.get(i), operations.get(i + 1), entry.getKey()));
            }
        }
        return moves;
    }
}

