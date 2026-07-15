package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Operation;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.MachineSequences;
import at.fhv.solver.impl.tabu.Move;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DwellRepairNeighbourhood implements INeighbourhood {
    private final INeighbourhood delegate;
    private final JsspProblem jsspProblem;

    public DwellRepairNeighbourhood(INeighbourhood delegate, JsspProblem jsspProblem) {
        this.delegate = delegate;
        this.jsspProblem = jsspProblem;
    }

    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences sequences) {
        List<Move> moves = new ArrayList<>(delegate.generate(evaluationResult, sequences));
        moves.addAll(repair(evaluationResult, sequences));
        return moves;
    }

    private List<Move> repair(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences sequences) {
        Set<Move> moves = new LinkedHashSet<>();

        for (Job job :jsspProblem.getJobs()) {
            List<Operation> operations = job.operations();
            for (int i = 0; i < operations.size() - 1; i++ ) {
                Operation operation = operations.get(i);
                if (!operation.hasDwellLimit()) { continue; }

                Operation successor = operations.get(i+1);
                Integer operationHead = evaluationResult.head().get(operation);
                Integer successorHead = evaluationResult.head().get(successor);
                if (operationHead == null || successorHead == null) {continue;}

                int operationEnd = operationHead + operation.processingTime();
                int dwell = successorHead - operationEnd;
                if (dwell <= operation.maxDwellTime()) {continue;}

                addSwapWithNeighbour(moves, sequences, successor, true);
                addSwapWithNeighbour(moves, sequences, operation, true);
                addSwapWithNeighbour(moves, sequences, successor, false);
                addSwapWithNeighbour(moves, sequences, operation, false);
            }
        }
        return new ArrayList<>(moves);
    }

    private void addSwapWithNeighbour(Set<Move> moves, MachineSequences sequences, Operation operation, boolean withPredecessor) {
        Operation neighbour = withPredecessor ? sequences.machinePredecessor(operation) : sequences.machineSuccessor(operation);
        if (neighbour == null) {return;}
        moves.add(new Move(operation, neighbour, operation.machineId()));
    }
}
