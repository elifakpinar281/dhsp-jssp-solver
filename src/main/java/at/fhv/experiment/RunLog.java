package at.fhv.experiment;

import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.solver.impl.TabuSearch;

import java.util.List;
import java.util.Map;

public record RunLog(
        String runId,
        String timestamp,
        String instance,
        String instanceKey,
        String algorithm,
        Map<String, Object> params,
        int jobCount,
        int machineCount,
        Integer makespan,
        boolean valid,
        long timeMs,
        long peakHeap,
        long expanded,
        int reached,
        int maxDepth,
        List<TabuSearch.IterationSnapshot> iterations,
        List<ScheduledOperation> schedule,
        List<String> violations
) {}
