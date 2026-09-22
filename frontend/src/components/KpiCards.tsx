import type { RunLog } from "../types";
import { formatInt, formatMakespan, formatMemory, formatRuntime } from "../format";

interface Kpi {
    label: string;
    value: string;
}

export default function KpiCards({ run }: { run: RunLog }) {
    const cards: Kpi[] = [
        { label: "Makespan", value: formatMakespan(run.makespan) },
        { label: "Runtime (wall)", value: formatRuntime(run.timeMs) },
        { label: "Peak Memory", value: formatMemory(run.peakHeapKb, run.peakHeap) },
        { label: "Expanded", value: formatInt(run.expanded) },
        { label: "Reached", value: formatInt(run.reached) },
        { label: "Max Depth", value: formatInt(run.maxDepth) },
    ];

    const aside: Kpi[] = [
        { label: "CPU time (thread)", value: formatRuntime(run.cpuMs) },
    ];
    if (run.processCpuMs !== undefined) {
        aside.push({ label: "CPU time (process)", value: formatRuntime(run.processCpuMs) });
    }
    if (run.gcMs !== undefined) {
        aside.push({ label: "GC time", value: formatRuntime(run.gcMs) });
    }
    if (run.timeToBestMs !== undefined && run.timeToBestMs !== null) {
        aside.push({ label: "Time to best", value: formatRuntime(run.timeToBestMs) });
    }
    if (run.peakHeapAfterGc !== undefined) {
        aside.push({ label: "Memory measured", value: run.peakHeapAfterGc ? "live after GC" : "used heap (no GC)" });
    }
    aside.push({ label: "Budget", value: run.stoppedByLimit ? "limit reached" : "OK" });

    return (
        <div className="kpi-block">
            <div className="kpi-row">
                {cards.map((kpi) => (
                    <div className="kpi-card" key={kpi.label}>
                        <div className="kpi-label">{kpi.label}</div>
                        <div className="kpi-value">{kpi.value}</div>
                    </div>
                ))}
            </div>
            <div className="kpi-aside">
                {aside.map((kpi) => (
                    <span className="kpi-chip" key={kpi.label}>
                        <span className="kpi-chip-label">{kpi.label}</span>
                        <span className="kpi-chip-value">{kpi.value}</span>
                    </span>
                ))}
            </div>
        </div>
    );
}