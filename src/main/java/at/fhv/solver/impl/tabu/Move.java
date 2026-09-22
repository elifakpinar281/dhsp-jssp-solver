package at.fhv.solver.impl.tabu;

import at.fhv.model.jssp.Operation;

import java.util.List;

public record Move (
        Operation operationA,
        Operation operationB,
        int machineId,
        MoveType type,
        List<Move> segment
){
    public enum MoveType{
        SWAP,
        MOVE_AFTER,
        MOVE_BEFORE,
        SEGMENT_SWAP
    }

    public Move(Operation operationA, Operation operationB, int machineId) {
        this(operationA, operationB, machineId, MoveType.SWAP, null);
    }

    public Move(Operation operationA, Operation operationB, int machineId, MoveType type) {
        this(operationA, operationB, machineId, type, null);
    }

    public record MoveAttribute(
            int jobA,
            int operationA,
            int jobB,
            int operationB,
            int machineId,
            MoveType type
    ) {}

    public MoveAttribute getAttribute() {
        Operation first = operationA;
        Operation second = operationB;

        boolean isSwap = type == MoveType.SWAP || type == MoveType.SEGMENT_SWAP;
        if (isSwap && isAfter(operationA, operationB)) {
            first = operationB;
            second = operationA;
        }

        return new MoveAttribute(first.jobId(), first.operationId(), second.jobId(), second.operationId(), machineId, type);
    }

    private boolean isAfter(Operation a, Operation b) {
        if (a.jobId() != b.jobId()) { return a.jobId() > b.jobId(); }
        return a.operationId() > b.operationId();
    }
}
