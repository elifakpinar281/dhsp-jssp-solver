package at.fhv.model.jssp;

public record Operation(
        int operationId,
        int jobId,
        int machineId,
        int processingTime,
        int maxDwellTime
) {
    public static final int NO_LIMIT = Integer.MAX_VALUE;

    public Operation(int operationId, int jobId, int machineId, int processingTime) {
        this(operationId, jobId, machineId, processingTime, NO_LIMIT);
    }

    public boolean hasDwellLimit() {
        return maxDwellTime != NO_LIMIT;
    }
}
