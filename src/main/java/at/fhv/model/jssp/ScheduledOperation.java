package at.fhv.model.jssp;

public record ScheduledOperation(
        int jobId,
        int machineId,
        int startTime,
        int endTime
) {}
