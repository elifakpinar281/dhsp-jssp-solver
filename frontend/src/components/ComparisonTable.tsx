import type { RunLog } from "../types";
import { distinctParamValues, memoryKb, primaryParamKey } from "../analysis/grouping";
import { formatInt, formatMemory, formatRuntime } from "../format";
import { paramLabel } from "../labels";

interface Row {
  label: string;
  bestMakespan: number;
  meanRuntimeMs: number;
  meanMemoryKb: number;
  meanExpanded: number;
  count: number;
}

function aggregate(runs: RunLog[]): Row | null {
  let bestMakespan = Number.POSITIVE_INFINITY;
  let runtimeSum = 0;
  let memorySum = 0;
  let expandedSum = 0;
  let valid = 0;
  for (const run of runs) {
    if (run.valid && run.makespan !== null) {
      if (run.makespan < bestMakespan) { bestMakespan = run.makespan; }
      runtimeSum += run.timeMs;
      memorySum += memoryKb(run);
      expandedSum += run.expanded;
      valid++;
    }
  }
  if (valid === 0) { return null; }
  return { label: "", bestMakespan, meanRuntimeMs: runtimeSum / valid, meanMemoryKb: memorySum / valid, meanExpanded: expandedSum / valid, count: runs.length };
}

export default function ComparisonTable({ algorithmRuns }: { algorithmRuns: RunLog[] }) {
  const key = primaryParamKey(algorithmRuns);
  const rows: Row[] = [];

  if (key === null) {
    const row = aggregate(algorithmRuns);
    if (row !== null) { rows.push({ ...row, label: "—" }); }
  } else {
    for (const value of distinctParamValues(algorithmRuns, key)) {
      const matching = algorithmRuns.filter((run) => String(run.params[key]) === value);
      const row = aggregate(matching);
      if (row !== null) { rows.push({ ...row, label: value }); }
    }
  }

  if (rows.length === 0) {
    return <div className="hint">No valid runs to compare.</div>;
  }

  return (
    <table className="data-table">
      <thead>
        <tr>
          <th>{key === null ? "Config" : paramLabel(key)}</th>
          <th>Makespan</th>
          <th>Mean Runtime</th>
          <th>Mean Memory</th>
          <th>Mean Expanded</th>
          <th>Runs</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.label}>
            <td className="mono">{row.label}</td>
            <td>{formatInt(row.bestMakespan)} s</td>
            <td>{formatRuntime(Math.round(row.meanRuntimeMs))}</td>
            <td>{formatMemory(Math.round(row.meanMemoryKb), 0)}</td>
            <td>{formatInt(Math.round(row.meanExpanded))}</td>
            <td>{row.count}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
