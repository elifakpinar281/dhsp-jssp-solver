package at.fhv.model.jssp;

public record Machine(
        int machineId,
        int capacity
) {
    public Machine(int machineId) {
        this(machineId, 1);
    }

    public Machine {
        if (capacity < 1) {
            throw new IllegalArgumentException("Machine " + machineId + ": capacity must be >= 1 but was " + capacity);
        }
    }
}
