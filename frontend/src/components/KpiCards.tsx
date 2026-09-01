import type { RunLog } from "../types";
import { formatInt, formatMakespan, formatMemory, formatRuntime } from "../format";

interface Kpi {
  label: string;
  value: string;
}

export default function KpiCards({ run }: { run: RunLog }) {
  const kpis: Kpi[] = [
    { label: "Makespan", value: formatMakespan(run.makespan) },
    { label: "Runtime", value: formatRuntime(run.timeMs) },
    { label: "Peak Memory", value: formatMemory(run.peakHeapKb, run.peakHeap) },
    { label: "Expanded", value: formatInt(run.expanded) },
    { label: "Reached", value: formatInt(run.reached) },
    { label: "Max Depth", value: formatInt(run.maxDepth) },
  ];

  return (
    <div className="kpi-row">
      {kpis.map((kpi) => (
        <div className="kpi-card" key={kpi.label}>
          <div className="kpi-label">{kpi.label}</div>
          <div className="kpi-value">{kpi.value}</div>
        </div>
      ))}
    </div>
  );
}
