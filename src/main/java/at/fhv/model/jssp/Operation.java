package at.fhv.model.jssp;

public record Operation(
        int operationId,
        int jobId,
        int machineId,
        int processingTime
) {}
