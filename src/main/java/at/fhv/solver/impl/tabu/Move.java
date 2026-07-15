package at.fhv.solver.impl.tabu;

import at.fhv.model.jssp.Operation;

public record Move (
        Operation operationA,
        Operation operationB,
        int machineId
){
    public record MoveAttribute(
            int jobA,
            int operationA,
            int jobB,
            int operationB,
            int machineId
    ) {}

    public MoveAttribute getAttribute() {
        Operation first = operationA;
        Operation second = operationB;

        boolean swap = operationA.jobId() > operationB.jobId() || (operationA.jobId() == operationB.jobId() && operationA.operationId() > operationB.operationId());

        if (swap) {
            first = operationB;
            second = operationA;
        }

        return new MoveAttribute(first.jobId(), first.operationId(), second.jobId(), second.operationId(), machineId);
    }
}