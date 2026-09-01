package at.fhv.model.fjssp;

import java.util.ArrayList;
import java.util.List;

public record FjsspOperation(
        int operationId,
        int jobId,
        int stationId,
        List<MachineOption> options,
        int maxDwellTime
) {
    public static final int NO_LIMIT = Integer.MAX_VALUE;

    public FjsspOperation(int operationId, int jobId, int stationId, List<MachineOption> options) {
        this(operationId, jobId, stationId, options, NO_LIMIT);
    }

    public boolean hasDwellLimit() {
        return maxDwellTime != NO_LIMIT;
    }

    public List<Integer> eligibleMachines() {
        List<Integer> machines = new ArrayList<>();

        for (MachineOption option : options) {
            machines.add(option.machineId());
        }
        return machines;
    }

    // Processing time auf einer konkreten Maschine
    public int processingOnOneMachine(int machineId) {
        for (MachineOption option : options) {
            if (option.machineId() == machineId) { return option.processingTime();}
        }
        throw new IllegalArgumentException("Operation " + operationId + " of job " + jobId + " is not allowed on machine " + machineId);
    }

    // Kürzeste processing time -> untere schranke
    public int minimalProcessingTime() {
        int min = Integer.MAX_VALUE;
        for (MachineOption option : options) {
            if (option.processingTime() < min) { min = option.processingTime(); }
        }

        return min == Integer.MAX_VALUE ? 0 : min;
    }
}
