import type { IterationTriple, RunLog, ScheduledOp } from "../types";

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

function toIterationTriple(raw: unknown): IterationTriple | null {
    if (Array.isArray(raw)) {
        const iteration = raw[0];
        const current = raw[1];
        const best = raw[2];
        if (typeof iteration !== "number" || typeof current !== "number" || typeof best !== "number") { return null; }
        return [iteration, current, best];
    }
    if (raw === null || typeof raw !== "object") { return null; }
    const value = raw as Record<string, unknown>;
    const iteration = value.iteration;
    const current = value.makespan;
    const best = value.bestMakespan;
    if (typeof iteration !== "number" || typeof current !== "number" || typeof best !== "number") { return null; }
    return [iteration, current, best];
}

function toIterations(raw: unknown): IterationTriple[] | null {
    if (!Array.isArray(raw)) { return null; }
    const triples: IterationTriple[] = [];
    for (const item of raw) {
        const triple = toIterationTriple(item);
        if (triple !== null) { triples.push(triple); }
    }
    return triples;
}

function toProblemMode(value: Record<string, unknown>, fallbackId: string): string | undefined {
    const raw = value.mode ?? value.problem;
    if (typeof raw === "string" && raw.length > 0) { return raw.toUpperCase(); }
    if (fallbackId.includes("_FJSSP_") || fallbackId.endsWith("_FJSSP")) { return "FJSSP"; }
    if (fallbackId.includes("_JSSP_") || fallbackId.endsWith("_JSSP")) { return "JSSP"; }
    return undefined;
}

function toAllocatedBytes(raw: unknown): number | undefined {
    if (typeof raw !== "number" || raw < 0) { return undefined; }
    return raw;
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
        mode: toProblemMode(value, fallbackId),
        params: (value.params as Record<string, number | string | boolean>) ?? {},
        jobCount: typeof value.jobCount === "number" ? value.jobCount : 0,
        machineCount: typeof value.machineCount === "number" ? value.machineCount : 0,
        makespan: typeof value.makespan === "number" ? value.makespan : null,
        valid: value.valid === true,
        timeMs: typeof value.timeMs === "number" ? value.timeMs : 0,
        cpuMs: typeof value.cpuMs === "number" ? value.cpuMs : 0,
        processCpuMs: typeof value.processCpuMs === "number" ? value.processCpuMs : undefined,
        gcMs: typeof value.gcMs === "number" ? value.gcMs : undefined,
        peakHeap: typeof value.peakHeap === "number" ? value.peakHeap : 0,
        peakHeapKb: typeof value.peakHeapKb === "number" ? value.peakHeapKb : undefined,
        peakHeapAfterGc: typeof value.peakHeapAfterGc === "boolean" ? value.peakHeapAfterGc : undefined,
        allocatedBytes: toAllocatedBytes(value.allocatedBytes),
        peakFrontier: typeof value.peakFrontier === "number" ? value.peakFrontier : undefined,
        timeToBestMs: typeof value.timeToBestMs === "number" ? value.timeToBestMs : null,
        expanded: typeof value.expanded === "number" ? value.expanded : 0,
        reached: typeof value.reached === "number" ? value.reached : 0,
        maxDepth: typeof value.maxDepth === "number" ? value.maxDepth : 0,
        stoppedByLimit: value.stoppedByLimit === true,
        violations: Array.isArray(value.violations) ? (value.violations as string[]) : [],
        iterations: toIterations(value.iterations),
        schedule: toSchedule(value.schedule),
    };
}