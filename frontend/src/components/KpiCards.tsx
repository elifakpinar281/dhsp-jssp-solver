import type { RunLog } from "../types";
import { formatInt, formatMakespan, formatMemory, formatRuntime } from "../format";

interface Kpi {
    label: string;
    value: string;
    hint: string;
    alert: string | null;
}

interface SearchMetrics {
    effortLabel: string;
    effortHint: string;
    reachedLabel: string;
    reachedHint: string | null;
    depthHint: string | null;
    frontierHint: string | null;
}

const DEPTH_HINT = "Deepest partial schedule the search looked at (number of scheduling steps from the start).";
const NOT_MEASURED = "—";

function searchMetricsFor(algorithm: string): SearchMetrics {
    switch (algorithm.toUpperCase()) {
        case "TABU":
            return {
                effortLabel: "Evaluated schedules",
                effortHint: "How many complete schedules Tabu Search calculated a makespan for.",
                reachedLabel: "Reached",
                reachedHint: null,
                depthHint: null,
                frontierHint: null,
            };
        case "GREEDY":
        case "ASTAR":
            return {
                effortLabel: "Expanded nodes",
                effortHint: "How many partial schedules the search took from the queue and extended by one step. Main measure of search work.",
                reachedLabel: "Unique states",
                reachedHint: "Different partial schedules the search discovered (duplicates are counted once).",
                depthHint: DEPTH_HINT,
                frontierHint: "Largest open list during the search: partial schedules waiting to be expanded. Main indicator of how memory grows (space complexity).",
            };
        case "BEAM":
            return {
                effortLabel: "Expanded nodes",
                effortHint: "How many partial schedules in the beam were extended by one step. Main measure of search work.",
                reachedLabel: "Generated successors",
                reachedHint: "All successor schedules created, including the ones the beam threw away.",
                depthHint: DEPTH_HINT,
                frontierHint: "Largest beam kept at one depth. It can never be bigger than the beam width, so it mostly confirms the configured width.",
            };
        case "BULB":
            return {
                effortLabel: "Expanded nodes",
                effortHint: "How many partial schedules were extended by one step, summed over all backtracking rounds.",
                reachedLabel: "Stored states",
                reachedHint: "Partial schedules kept in the duplicate check table.",
                depthHint: DEPTH_HINT,
                frontierHint: "Largest number of successors kept at one level (at most the beam width). The duplicate table (Stored states) usually needs more memory.",
            };
        case "BEAMSTACK":
            return {
                effortLabel: "Expanded nodes",
                effortHint: "How many partial schedules were extended by one step, summed over all backtracking rounds.",
                reachedLabel: "Generated successors",
                reachedHint: null,
                depthHint: DEPTH_HINT,
                frontierHint: "Largest layer of partial schedules kept at one depth (at most the beam width).",
            };
        default:
            return {
                effortLabel: "Expanded",
                effortHint: "Search work done by this algorithm.",
                reachedLabel: "Reached",
                reachedHint: "States the search discovered.",
                depthHint: DEPTH_HINT,
                frontierHint: "Largest number of partial schedules stored at the same time.",
            };
    }
}

function formatBytes(bytes: number): string {
    const kb = bytes / 1024;
    if (kb < 1024) {
        return kb.toFixed(1) + " KB";
    }
    const mb = kb / 1024;
    if (mb < 1024) {
        return mb.toFixed(1) + " MB";
    }
    return (mb / 1024).toFixed(2) + " GB";
}

// 8160 -> "2 h 16 min"
function formatDuration(totalSeconds: number): string {
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    if (hours === 0) {
        return minutes + " min";
    }
    return hours + " h " + minutes + " min";
}


function makespanKpi(run: RunLog): Kpi {
    if (run.makespan === null) {
        return {
            label: "Makespan",
            value: NOT_MEASURED,
            hint: "The algorithm did not return a complete schedule.",
            alert: "No schedule found",
        };
    }
    if (!run.valid) {
        return {
            label: "Makespan",
            value: formatMakespan(run.makespan),
            hint: "The schedule breaks " + run.violations.length + " rules of the plant. Do not compare this value with other runs.",
            alert: "Invalid schedule",
        };
    }
    return {
        label: "Makespan",
        value: formatMakespan(run.makespan),
        hint: "≈ " + formatDuration(run.makespan) + ". Time until the last job is finished. Lower is better.",
        alert: null,
    };
}

function runtimeKpi(run: RunLog): Kpi {
    return {
        label: "Runtime",
        value: formatRuntime(run.timeMs),
        hint: "Real time from start to end of the search (stopwatch time).",
        alert: null,
    };
}

function timeToBestKpi(run: RunLog): Kpi {
    if (run.timeToBestMs === undefined || run.timeToBestMs === null) {
        return { label: "Time to best", value: NOT_MEASURED, hint: "No solution was found, so there is no best result.", alert: null };
    }
    let hint = "When the final result was found.";
    if (run.timeMs > 0) {
        const percent = Math.round((run.timeToBestMs / run.timeMs) * 100);
        hint = "Final result found after " + percent + " % of the runtime. Afterwards the search did not improve anymore.";
    }
    return { label: "Time to best", value: formatRuntime(run.timeToBestMs), hint: hint, alert: null };
}

function memoryKpi(run: RunLog): Kpi {
    let hint = "Highest amount of live data on the heap after garbage collection. Includes the loaded problem, not only the search.";
    if (run.peakHeapAfterGc !== true) {
        hint = "Highest heap usage seen during the run, including garbage that was not yet cleaned up. Tends to be too high.";
    } else if (run.gcMs === 0) {
        hint = "Not reliable for this run: no garbage collection happened during the search, so this value probably comes from before the run.";
    }
    hint = hint + " Secondary value: prefer Peak frontier and Allocated for comparisons.";
    return { label: "Peak heap", value: formatMemory(run.peakHeapKb, run.peakHeap), hint: hint, alert: null };
}

function budgetKpi(run: RunLog): Kpi {
    if (run.stoppedByLimit) {
        return {
            label: "Budget",
            value: "Limit reached",
            hint: "Stopped by the expansion or time limit. With more budget the result could be better.",
            alert: "Stopped early",
        };
    }
    return { label: "Budget", value: "OK", hint: "Search finished on its own, no limit was hit.", alert: null };
}


function cpuKpi(run: RunLog): Kpi {
    let hint = "Time the CPU actually worked on the search. Close to the runtime means the search was not waiting.";
    if (run.processCpuMs !== undefined) {
        hint = hint + " Whole JVM incl. JIT compiler and GC threads: " + formatRuntime(run.processCpuMs) + " (can be bigger than the runtime).";
    }
    return { label: "CPU time", value: formatRuntime(run.cpuMs), hint: hint, alert: null };
}

function gcKpi(run: RunLog): Kpi {
    if (run.gcMs === undefined) {
        return { label: "GC time", value: NOT_MEASURED, hint: "Not recorded in this run file.", alert: null };
    }
    return {
        label: "GC time",
        value: formatRuntime(run.gcMs),
        hint: "Time spent cleaning up unused memory. High values mean the search creates a lot of garbage.",
        alert: null,
    };
}

function allocatedKpi(run: RunLog): Kpi {
    if (run.allocatedBytes === undefined) {
        return { label: "Allocated", value: NOT_MEASURED, hint: "Not recorded in this run file (older run, or not supported by the JVM).", alert: null };
    }
    return {
        label: "Allocated",
        value: formatBytes(run.allocatedBytes),
        hint: "Total memory the search thread requested, including objects that were freed again. Measures allocation volume, not peak memory. Does not depend on garbage collection.",
        alert: null,
    };
}

function frontierKpi(run: RunLog, search: SearchMetrics): Kpi {
    if (search.frontierHint === null) {
        return { label: "Peak frontier", value: NOT_MEASURED, hint: notMeasuredHint(run.algorithm), alert: null };
    }
    if (run.peakFrontier === undefined) {
        return { label: "Peak frontier", value: NOT_MEASURED, hint: "Not recorded in this run file (older run).", alert: null };
    }
    return { label: "Peak frontier", value: formatInt(run.peakFrontier), hint: search.frontierHint, alert: null };
}

function notMeasuredHint(algorithm: string): string {
    if (algorithm.toUpperCase() === "TABU") {
        return "Tabu Search changes complete schedules and has no search tree, so this value does not exist.";
    }
    return "Not measured reliably for this algorithm.";
}

function renderCard(kpi: Kpi) {
    return (
        <div className="kpi-card" key={kpi.label} tabIndex={0}>
            <div className="kpi-label">
                {kpi.label}
            </div>
            <div className="kpi-value">{kpi.value}</div>
            {kpi.alert !== null ? <div className="kpi-hint-warning">{kpi.alert}</div> : null}
            <div className="kpi-tooltip" role="tooltip">{kpi.hint}</div>
        </div>
    );
}

function renderChip(kpi: Kpi) {
    return (
        <span className={kpi.alert !== null ? "kpi-chip kpi-chip-alert" : "kpi-chip"} key={kpi.label} tabIndex={0}>
            <span className="kpi-chip-label">{kpi.label}</span>
            <span className="kpi-chip-value">{kpi.value}</span>
            <span className="kpi-tooltip" role="tooltip">{kpi.hint}</span>
        </span>
    );
}

export default function KpiCards({ run }: { run: RunLog }) {
    const search = searchMetricsFor(run.algorithm);

    const resultCards: Kpi[] = [
        makespanKpi(run),
        runtimeKpi(run),
        cpuKpi(run),
        memoryKpi(run),
    ];

    const searchCards: Kpi[] = [
        {
            label: search.reachedLabel,
            value: search.reachedHint !== null ? formatInt(run.reached) : NOT_MEASURED,
            hint: search.reachedHint !== null ? search.reachedHint : notMeasuredHint(run.algorithm),
            alert: null,
        },
        {
            label: "Max depth",
            value: search.depthHint !== null ? formatInt(run.maxDepth) : NOT_MEASURED,
            hint: search.depthHint !== null ? search.depthHint : notMeasuredHint(run.algorithm),
            alert: null,
        },
        frontierKpi(run, search),
        allocatedKpi(run),
    ];

    const infoChips: Kpi[] = [
        budgetKpi(run),
        { label: search.effortLabel, value: formatInt(run.expanded), hint: search.effortHint, alert: null },
        gcKpi(run),
        timeToBestKpi(run),
    ];

    return (
        <div className="kpi-block">
            <div className="kpi-grid">
                {resultCards.map((kpi) => renderCard(kpi))}
            </div>
            <div className="kpi-grid kpi-grid-small">
                {searchCards.map((kpi) => renderCard(kpi))}
            </div>
            <div className="kpi-chips">
                {infoChips.map((kpi) => renderChip(kpi))}
            </div>
        </div>
    );
}