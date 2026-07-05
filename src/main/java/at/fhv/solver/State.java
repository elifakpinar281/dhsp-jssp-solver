package at.fhv.solver;

import at.fhv.model.jssp.ScheduledOperation;

import java.util.Arrays;
import java.util.List;

public record State(
        int[] nextOperation,
        int[] machineAvailableTime,
        int[] jobAvailableTime,
        List<ScheduledOperation> scheduledOperations
) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof State state)) {
            return false;
        }

        return Arrays.equals(nextOperation, state.nextOperation)
                && Arrays.equals(machineAvailableTime, state.machineAvailableTime)
                && Arrays.equals(jobAvailableTime, state.jobAvailableTime);

    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(nextOperation);
        result = 31 * result + Arrays.hashCode(machineAvailableTime);
        result = 31 * result + Arrays.hashCode(jobAvailableTime);
        return result;
    }
}
