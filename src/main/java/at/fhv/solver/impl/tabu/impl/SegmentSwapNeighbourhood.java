package at.fhv.solver.impl.tabu.impl;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Operation;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.MachineSequences;
import at.fhv.solver.impl.tabu.Move;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SegmentSwapNeighbourhood implements INeighbourhood {
    private final JsspProblem problem;

    public SegmentSwapNeighbourhood(JsspProblem problem) {
        this.problem = problem;
    }

    @Override
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences machineSequences) {
        List<Move> moves = new ArrayList<>();
        Set<Move.MoveAttribute> seen = new HashSet<>();

        for (Operation operation : evaluationResult.criticalPath()) {
            addSegmentMove(operation, machineSequences, moves, seen);

            int position = machineSequences.positionOf(operation);
            Operation previous = machineSequences.at(operation.machineId(), position - 1);
            if (previous != null) {
                addSegmentMove(previous, machineSequences, moves, seen);
            }
        }
        return moves;
    }

    private void addSegmentMove(Operation first, MachineSequences machineSequences, List<Move> moves, Set<Move.MoveAttribute> seen) {
        if (problem.capacityOf(first.machineId()) != 1) { return; }

        Operation second = nextOnMachine(first, machineSequences);
        if (second == null) { return; }
        int otherJob = second.jobId();
        if (otherJob == first.jobId()) { return; }

        List<Operation> route = problem.getJobs().get(first.jobId()).operations();
        int low = first.operationId();
        int high = first.operationId();

        while (low - 1 >= 0 && isDirectlyBefore(route.get(low - 1), otherJob, machineSequences)) {
            low = low - 1;
        }
        while (high + 1 < route.size() && isDirectlyBefore(route.get(high + 1), otherJob, machineSequences)) {
            high = high + 1;
        }

        List<Move> parts = new ArrayList<>();
        for (int i = low; i <= high; i++) {
            Operation a = route.get(i);
            Operation b = nextOnMachine(a, machineSequences);
            parts.add(new Move(a, b, a.machineId()));
        }

        Operation segmentStart = route.get(low);
        Move move = new Move(segmentStart, nextOnMachine(segmentStart, machineSequences),
                segmentStart.machineId(), Move.MoveType.SEGMENT_SWAP, parts);

        if (seen.add(move.getAttribute())) {
            moves.add(move);
        }
    }

    private boolean isDirectlyBefore(Operation operation, int otherJob, MachineSequences machineSequences) {
        if (problem.capacityOf(operation.machineId()) != 1) { return false; }
        Operation next = nextOnMachine(operation, machineSequences);
        return next != null && next.jobId() == otherJob;
    }

    private Operation nextOnMachine(Operation operation, MachineSequences machineSequences) {
        int position = machineSequences.positionOf(operation);
        return machineSequences.at(operation.machineId(), position + 1);
    }
}

