package at.fhv.solver;

import java.util.Arrays;

public record State(
        int[] nextOperation,
        int[] machineAvailableTime,
        int[] jobAvailableTime
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
