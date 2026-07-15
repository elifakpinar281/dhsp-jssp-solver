package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Operation;
import at.fhv.solver.impl.tabu.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DwellRepairNeighbourhood implements INeighbourhood {
    private static final int MAX_PULL_BACK = 3;

    private final INeighbourhood delegate;
    private final JsspProblem problem;

    public DwellRepairNeighbourhood(INeighbourhood delegate, JsspProblem problem) {
        this.delegate = delegate;
        this.problem = problem;
    }

    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences sequences) {
        Set<Move> moves = new LinkedHashSet<>(delegate.generate(evaluationResult, sequences));

        for (ScheduleEvaluator.DwellViolation violation : evaluationResult.dwellViolations()) {
            Operation operation = violation.operation();
            Operation successor = problem.jobSuccessor(operation);
            if (successor == null) { continue; }

            Operation predecessor = sequences.machinePredecessor(successor);
            int steps = 0;
            while (predecessor != null && steps < MAX_PULL_BACK) {
                moves.add(new Move(successor, predecessor, successor.machineId()));
                predecessor = sequences.machinePredecessor(predecessor);
                steps++;
            }

            Operation machineSuccessor = sequences.machineSuccessor(operation);
            if (machineSuccessor != null) {
                moves.add(new Move(operation, machineSuccessor, operation.machineId()));
            }
        }

        return new ArrayList<>(moves);
    }
}