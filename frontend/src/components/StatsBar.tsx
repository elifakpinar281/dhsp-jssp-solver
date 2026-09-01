import type { RunLog } from "../types";
import { groupStats } from "../analysis/metrics";
import { formatInt, formatRuntime } from "../format";

export default function StatsBar({ runs }: { runs: RunLog[] }) {
  const stats = groupStats(runs);
  const cells: { label: string; value: string }[] = [
    { label: "Runs", value: String(stats.count) },
    { label: "Best", value: formatInt(stats.best) + " s" },
    { label: "Mean", value: formatInt(Math.round(stats.mean)) + " s" },
    { label: "σ", value: formatInt(Math.round(stats.std)) + " s" },
    { label: "Worst Case", value: formatInt(stats.worst) + " s" },
    { label: "Spread", value: formatInt(stats.spread) + " s" },
    { label: "Mean Runtime", value: formatRuntime(Math.round(stats.meanRuntimeMs)) },
    { label: "Invalid", value: stats.invalid + " / " + stats.count },
  ];

  return (
    <div className="stats-bar">
      {cells.map((cell) => (
        <div key={cell.label}>
          <div className="sb-label">{cell.label}</div>
          <div className="sb-value">{cell.value}</div>
        </div>
      ))}
    </div>
  );
}
