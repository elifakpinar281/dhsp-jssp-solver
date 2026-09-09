"use client";

import { useState } from "react";
import { machineLoads, occupancyGrid } from "../analysis/metrics";
import type { RunLog, ScheduledOp } from "../types";
import { heatColor, utilizationColor } from "../colors";
import { machineName } from "../data/mapping";

const BIN_COUNT = 30;
const LABEL_WIDTH = 250;
const CELL_HEIGHT = 16;

type Hover =
    | { kind: "bn"; x: number; y: number; name: string; busy: number; makespan: number; util: number }
    | { kind: "cell"; x: number; y: number; name: string; binStart: number; binEnd: number; pct: number };

function scheduleEnd(schedule: ScheduledOp[]): number {
    let max = 0;
    for (const op of schedule) {
        if (op.e > max) { max = op.e; }
    }
    return max;
}

function seconds(value: number): string {
    return value.toLocaleString("de-AT") + " s";
}

export default function UtilizationHeatmap({ run }: { run: RunLog }) {
    const [hover, setHover] = useState<Hover | null>(null);
    const schedule = run.schedule ?? [];
    if (schedule.length === 0) {
        return <div className="hint">No schedule stored in this run.</div>;
    }

    const fjssp = run.mode === "FJSSP";
    const loads = machineLoads(run);
    const grid = occupancyGrid(run, BIN_COUNT);
    const makespan = run.makespan ?? scheduleEnd(schedule);
    const cellWidth = 760 / grid.binCount;
    const gridHeight = grid.machineIds.length * CELL_HEIGHT;
    const gridWidth = LABEL_WIDTH + grid.binCount * cellWidth + 20;
    const topBottlenecks = loads.slice(0, 6);

    return (
        <div className="heatmap-wrap" onMouseLeave={() => setHover(null)}>
            <div className="sub-title">Top bottlenecks (busy time / makespan)</div>
            <div className="bottleneck-list">
                {topBottlenecks.map((load) => {
                    const name = machineName(run.instanceKey, load.machineId, run.mode);
                    return (
                        <div
                            className="bottleneck-row"
                            key={load.machineId}
                            onMouseMove={(event) => setHover({
                                kind: "bn", x: event.clientX, y: event.clientY,
                                name, busy: load.busy, makespan, util: load.util,
                            })}
                        >
                            <span className="bn-name">{name}</span>
                            <span className="bn-bar-track">
                                <span className="bn-bar" style={{ width: Math.min(100, load.util * 100).toFixed(1) + "%", background: utilizationColor(load.util) }} />
                            </span>
                            <span className="bn-value">{(load.util * 100).toFixed(1)}%</span>
                        </div>
                    );
                })}
            </div>

            <div className="sub-title">Utilization over time (rows sorted by utilization)</div>
            <div className="chart-scroll">
                <svg width={gridWidth} height={gridHeight + 4} className="heatmap">
                    {grid.machineIds.map((machineId, row) => {
                        const name = machineName(run.instanceKey, machineId, run.mode);
                        return (
                            <g key={machineId}>
                                <text x={LABEL_WIDTH - 8} y={row * CELL_HEIGHT + CELL_HEIGHT - 4} className="row-label" textAnchor="end">
                                    {name}
                                </text>
                                {grid.cells[row].map((value, bin) => {
                                    const binStart = Math.round(bin * grid.binWidth);
                                    const binEnd = Math.round((bin + 1) * grid.binWidth);
                                    return (
                                        <rect
                                            key={bin}
                                            x={LABEL_WIDTH + bin * cellWidth}
                                            y={row * CELL_HEIGHT}
                                            width={cellWidth - 1}
                                            height={CELL_HEIGHT - 1}
                                            fill={heatColor(value, fjssp)}
                                            onMouseMove={(event) => setHover({
                                                kind: "cell", x: event.clientX, y: event.clientY,
                                                name, binStart, binEnd, pct: value * 100,
                                            })}
                                        />
                                    );
                                })}
                            </g>
                        );
                    })}
                </svg>
            </div>
            <div className="legend">
                <span>0%</span>
                <span className="legend-gradient" />
                <span>100% busy</span>
            </div>

            {hover !== null ? (
                <div className="gantt-tooltip" style={{ left: hover.x + 14, top: hover.y + 14 }}>
                    <div className="tt-title">{hover.name}</div>
                    {hover.kind === "bn" ? (
                        <>
                            <div className="tt-row">Busy <b>{seconds(hover.busy)}</b> of <b>{seconds(hover.makespan)}</b></div>
                            <div className="tt-row">Utilisation <b>{(hover.util * 100).toFixed(1)}%</b></div>
                            <div className="tt-row">Higher utilisation &rarr; tighter constraint on the makespan.</div>
                        </>
                    ) : (
                        <>
                            <div className="tt-row">Time slot <b>{seconds(hover.binStart)} &ndash; {seconds(hover.binEnd)}</b></div>
                            <div className="tt-row">Busy <b>{hover.pct.toFixed(0)}%</b> of this slot</div>
                        </>
                    )}
                </div>
            ) : null}
        </div>
    );
}