package at.fhv.solver.implementation.tabu;

import at.fhv.model.jssp.Operation;

public record Move (
        Operation operationA,
        Operation operationB,
        int machineId
){
    public record MoveAttribute(
            int operationA,
            int operationB,
            int machineId
    ) {}

    public MoveAttribute getAttribute() {
        int idA = operationA.operationId();
        int idB = operationB.operationId();
        return new MoveAttribute( Math.min(idA, idB), Math.max(idA, idB), machineId );
    }
}