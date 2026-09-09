package at.fhv.experiment;

import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.solver.impl.tabu.TabuSearch;

import java.util.List;
import java.util.Map;

public record RunLog(
        String runId,
        String timestamp,
        String instance,
        String instanceKey,
        String algorithm,
        String problem,
        Map<String, Object> params,
        int jobCount,
        int machineCount,
        Integer makespan,
        boolean valid,
        long timeMs,
        long cpuMs,
        long peakHeap,
        long expanded,
        int reached,
        int maxDepth,
        boolean stoppedByLimit,
        List<TabuSearch.IterationSnapshot> iterations,
        List<ScheduledOperation> schedule,
        List<String> violations
) {}