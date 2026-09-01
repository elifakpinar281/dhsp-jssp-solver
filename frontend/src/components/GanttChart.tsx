"use client";

import { useState } from "react";
import type { RunLog, ScheduledOp } from "../types";
import { jobColor } from "../colors";
import { machineName } from "../data/mapping";

const LABEL_WIDTH = 250;
const PLOT_WIDTH = 760;
const ROW_HEIGHT = 18;
const TOP = 28;

interface HoverInfo {
    x: number;
    y: number;
    jobId: number;
    step: number;
    stepCount: number;
    machineId: number;
    start: number;
    end: number;
}

export default function GanttChart({ run }: { run: RunLog }) {
    const [hover, setHover] = useState<HoverInfo | null>(null);
    const schedule = run.schedule ?? [];
    if (schedule.length === 0) {
        return <div className="hint">No schedule stored in this run.</div>;
    }

    const makespan = run.makespan ?? 1;

    const stepByOp = new Map<ScheduledOp, { step: number; count: number }>();
    const byJob = new Map<number, ScheduledOp[]>();
    for (const op of schedule) {
        const list = byJob.get(op.j) ?? [];
        list.push(op);
        byJob.set(op.j, list);
    }
    for (const [, list] of byJob) {
        list.sort((a, b) => a.s - b.s);
        for (let i = 0; i < list.length; i++) {
            stepByOp.set(list[i], { step: i + 1, count: list.length });
        }
    }

    const machineIds: number[] = [];
    for (const op of schedule) {
        if (!machineIds.includes(op.m)) { machineIds.push(op.m); }
    }
    machineIds.sort((a, b) => a - b);
    const rowIndex = new Map<number, number>();
    for (let i = 0; i < machineIds.length; i++) { rowIndex.set(machineIds[i], i); }

    const scale = PLOT_WIDTH / makespan;
    const height = TOP + machineIds.length * ROW_HEIGHT + 10;
    const width = LABEL_WIDTH + PLOT_WIDTH + 20;

    const ticks: number[] = [];
    const tickStep = niceStep(makespan / 6);
    for (let t = 0; t <= makespan; t += tickStep) { ticks.push(t); }

    return (
        <div className="chart-scroll gantt-wrap" onMouseLeave={() => setHover(null)}>
            <svg width={width} height={height} className="gantt">
                {ticks.map((t) => (
                    <g key={"tick-" + t}>
                        <line x1={LABEL_WIDTH + t * scale} y1={TOP - 6} x2={LABEL_WIDTH + t * scale} y2={height - 10} className="grid" />
                        <text x={LABEL_WIDTH + t * scale} y={TOP - 12} className="axis-label" textAnchor="middle">{formatTick(t)}</text>
                    </g>
                ))}
                <text x={LABEL_WIDTH + PLOT_WIDTH} y={height - 1} className="axis-title" textAnchor="end">Time (s)</text>
                {machineIds.map((machineId) => {
                    const row = rowIndex.get(machineId) ?? 0;
                    const y = TOP + row * ROW_HEIGHT;
                    return (
                        <text key={"lab-" + machineId} x={LABEL_WIDTH - 8} y={y + ROW_HEIGHT - 5} className="row-label" textAnchor="end">
                            {machineName(run.instanceKey, machineId, run.mode)}
                        </text>
                    );
                })}
                {schedule.map((op, index) => {
                    const row = rowIndex.get(op.m) ?? 0;
                    const y = TOP + row * ROW_HEIGHT;
                    const x = LABEL_WIDTH + op.s * scale;
                    const w = Math.max(1.5, (op.e - op.s) * scale);
                    const info = stepByOp.get(op) ?? { step: 1, count: 1 };
                    return (
                        <rect key={index} x={x} y={y + 1.5} width={w}
                              height={ROW_HEIGHT - 4} rx={2} fill={jobColor(op.j)}
                              className="gantt-bar"
                              onMouseMove={(event) => setHover({
                                  x: event.clientX, y: event.clientY,
                                  jobId: op.j, step: info.step, stepCount: info.count,
                                  machineId: op.m, start: op.s, end: op.e,
                              })}
                        />
                    );
                })}
            </svg>
            {hover !== null ? (
                <div className="gantt-tooltip" style={{ left: hover.x + 14, top: hover.y + 14 }}>
                    <div className="tt-title" style={{ color: jobColor(hover.jobId) }}>Job {hover.jobId} · Step {hover.step}/{hover.stepCount}</div>
                    <div className="tt-station">{machineName(run.instanceKey, hover.machineId, run.mode)}</div>
                    <div className="tt-row">Start <b>{hover.start.toLocaleString("de-AT")} s</b> → End <b>{hover.end.toLocaleString("de-AT")} s</b></div>
                    <div className="tt-row">Duration <b>{(hover.end - hover.start).toLocaleString("de-AT")} s</b></div>
                </div>
            ) : null}
        </div>
    );
}

function formatTick(t: number): string {
    return t.toLocaleString("de-AT") + "s";
}

function niceStep(rough: number): number {
    const power = Math.pow(10, Math.floor(Math.log10(rough)));
    const candidates = [1, 2, 2.5, 5, 10];
    for (const candidate of candidates) {
        if (candidate * power >= rough) { return candidate * power; }
    }
    return 10 * power;
}