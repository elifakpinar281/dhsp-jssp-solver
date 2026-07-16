package at.fhv.solver.impl.tabu;

import at.fhv.model.jssp.Operation;

public record Move (
        Operation operationA,
        Operation operationB,
        int machineId,
        MoveType type
){
    public enum MoveType{
        SWAP,
        MOVE_AFTER,
        MOVE_BEFORE
    }

    public Move(Operation operationA, Operation operationB, int machineId) {
        this(operationA, operationB, machineId, MoveType.SWAP);
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

        if (type == MoveType.SWAP && isAfter(operationA, operationB)) {
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