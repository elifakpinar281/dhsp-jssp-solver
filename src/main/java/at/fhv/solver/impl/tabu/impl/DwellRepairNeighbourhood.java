package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.Operation;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.solver.impl.tabu.*;

import java.util.*;

public class DwellRepairNeighbourhood implements INeighbourhood {
    private final INeighbourhood delegate;

    public DwellRepairNeighbourhood(INeighbourhood delegate, JsspProblem problem) {
        this.delegate = delegate;
    }


    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences sequences) {
        Set<Move> moves = new LinkedHashSet<>();
        moves.addAll(delegate.generate(evaluationResult, sequences));

        for(ScheduleEvaluator.DwellViolation violation : evaluationResult.dwellViolations()) {
            Operation op = violation.operation();
            Operation pred = sequences.machinePredecessor(op);
            Operation succ = sequences.machineSuccessor(op);

            if(pred != null) {
                moves.add(new Move(op, pred, op.machineId()));
            }


            if(succ != null) {
                moves.add(new Move(op, succ, op.machineId()));
            }
        }
        return new ArrayList<>(moves);
    }
}