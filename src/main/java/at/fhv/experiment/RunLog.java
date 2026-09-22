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
        long processCpuMs,
        long gcMs,
        long peakHeap,
        long peakHeapKb,
        boolean peakHeapAfterGc,
        long expanded,
        int reached,
        int maxDepth,
        boolean stoppedByLimit,
        List<TabuSearch.IterationSnapshot> iterations,
        List<ScheduledOperation> schedule,
        List<String> violations,
        long seed,
        int cores,
        Integer lowerBound,
        Double gapPercent,
        long evaluations,
        Long timeToBestMs,
        long expansionsToBest,
        long timeLimitMs
) {}
