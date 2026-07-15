package at.fhv.solver;

import java.util.Arrays;

public record State(
        int[] nextOperation,
        int[] bathAvailableTime,
        int[] jobAvailableTime,
        int[] jobBath
) {
    public static final int NO_BATH = -1;
    public static final int BLOCKED = Integer.MAX_VALUE;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof State state)) {
            return false;
        }

        return Arrays.equals(nextOperation, state.nextOperation)
                && Arrays.equals(bathAvailableTime, state.bathAvailableTime)
                && Arrays.equals(jobAvailableTime, state.jobAvailableTime)
                && Arrays.equals(jobBath, state.jobBath);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(nextOperation);
        result = 31 * result + Arrays.hashCode(bathAvailableTime);
        result = 31 * result + Arrays.hashCode(jobAvailableTime);
        result = 31 * result + Arrays.hashCode(jobBath);
        return result;
    }
}