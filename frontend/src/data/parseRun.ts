import type { RunLog, ScheduledOp } from "../types";

function toScheduledOp(raw: unknown): ScheduledOp | null {
    if (raw === null || typeof raw !== "object") { return null; }
    const value = raw as Record<string, unknown>;
    const j = value.j ?? value.jobId;
    const m = value.m ?? value.machineId;
    const s = value.s ?? value.startTime;
    const e = value.e ?? value.endTime;
    if (typeof j !== "number" || typeof m !== "number" || typeof s !== "number" || typeof e !== "number") { return null;}
    return { j, m, s, e };
}

function toSchedule(raw: unknown): ScheduledOp[] | null {
    if (!Array.isArray(raw)) { return null; }
    const ops: ScheduledOp[] = [];
    for (const item of raw) {
        const op = toScheduledOp(item);
        if (op !== null) { ops.push(op); }
    }
    return ops;
}

export function toRunLog(raw: unknown, fallbackId: string): RunLog | null {
    if (raw === null || typeof raw !== "object") { return null; }
    const value = raw as Record<string, unknown>;
    if (typeof value.algorithm !== "string") { return null; }
    return {
        runId: typeof value.runId === "string" ? value.runId : fallbackId,
        timestamp: typeof value.timestamp === "string" ? value.timestamp : "",
        instance: typeof value.instance === "string" ? value.instance : "",
        instanceKey: typeof value.instanceKey === "string" ? value.instanceKey : "",
        algorithm: value.algorithm,
        params: (value.params as Record<string, number | string | boolean>) ?? {},
        jobCount: typeof value.jobCount === "number" ? value.jobCount : 0,
        machineCount: typeof value.machineCount === "number" ? value.machineCount : 0,
        makespan: typeof value.makespan === "number" ? value.makespan : null,
        valid: value.valid === true,
        timeMs: typeof value.timeMs === "number" ? value.timeMs : 0,
        peakHeap: typeof value.peakHeap === "number" ? value.peakHeap : 0,
        peakHeapKb: typeof value.peakHeapKb === "number" ? value.peakHeapKb : undefined,
        expanded: typeof value.expanded === "number" ? value.expanded : 0,
        reached: typeof value.reached === "number" ? value.reached : 0,
        maxDepth: typeof value.maxDepth === "number" ? value.maxDepth : 0,
        violations: Array.isArray(value.violations) ? (value.violations as string[]) : [],
        iterations: Array.isArray(value.iterations) ? (value.iterations as RunLog["iterations"]) : null,
        schedule: toSchedule(value.schedule),
    };
}