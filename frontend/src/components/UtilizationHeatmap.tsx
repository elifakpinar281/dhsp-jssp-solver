import { machineLoads, occupancyGrid } from "../analysis/metrics";
import type { RunLog } from "../types";
import { heatColor, utilizationColor } from "../colors";
import { machineName } from "../data/mapping";

const BIN_COUNT = 30;
const LABEL_WIDTH = 250;
const CELL_HEIGHT = 16;

export default function UtilizationHeatmap({ run }: { run: RunLog }) {
    const schedule = run.schedule ?? [];
    if (schedule.length === 0) {
        return <div className="hint">No schedule stored in this run.</div>;
    }

    const fjssp = run.mode === "FJSSP";
    const loads = machineLoads(run);
    const grid = occupancyGrid(run, BIN_COUNT);
    const cellWidth = 760 / grid.binCount;
    const gridHeight = grid.machineIds.length * CELL_HEIGHT;
    const gridWidth = LABEL_WIDTH + grid.binCount * cellWidth + 20;
    const topBottlenecks = loads.slice(0, 6);

    return (
        <div className="heatmap-wrap">
            <div className="sub-title">Top bottlenecks (busy time / makespan)</div>
            <div className="bottleneck-list">
                {topBottlenecks.map((load) => (
                    <div className="bottleneck-row" key={load.machineId}>
            <span className="bn-name" title={machineName(run.instanceKey, load.machineId, run.mode)}>
              {machineName(run.instanceKey, load.machineId, run.mode)}
            </span>
                        <span className="bn-bar-track">
              <span className="bn-bar" style={{ width: Math.min(100, load.util * 100).toFixed(1) + "%", background: utilizationColor(load.util) }} />
            </span>
                        <span className="bn-value">{(load.util * 100).toFixed(1)}%</span>
                    </div>
                ))}
            </div>

            <div className="sub-title">Utilization over time (rows sorted by utilization)</div>
            <div className="chart-scroll">
                <svg width={gridWidth} height={gridHeight + 4} className="heatmap">
                    {grid.machineIds.map((machineId, row) => (
                        <g key={machineId}>
                            <text x={LABEL_WIDTH - 8} y={row * CELL_HEIGHT + CELL_HEIGHT - 4} className="row-label" textAnchor="end">
                                {machineName(run.instanceKey, machineId, run.mode)}
                            </text>
                            {grid.cells[row].map((value, bin) => (
                                <rect
                                    key={bin}
                                    x={LABEL_WIDTH + bin * cellWidth}
                                    y={row * CELL_HEIGHT}
                                    width={cellWidth - 1}
                                    height={CELL_HEIGHT - 1}
                                    fill={heatColor(value, fjssp)}
                                >
                                    <title>{machineName(run.instanceKey, machineId, run.mode) + " · " + (value * 100).toFixed(0) + "% busy"}</title>
                                </rect>
                            ))}
                        </g>
                    ))}
                </svg>
            </div>
            <div className="legend">
                <span>0%</span>
                <span className="legend-gradient" />
                <span>100% busy</span>
            </div>
        </div>
    );
}