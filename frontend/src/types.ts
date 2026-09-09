export interface ScheduledOp {
    j: number;
    m: number;
    s: number;
    e: number;
}

export type IterationTriple = [number, number, number];

export interface RunLog {
    runId: string;
    timestamp: string;
    instance: string;
    instanceKey: string;
    algorithm: string;
    mode?: "JSSP" | "FJSSP" | string;
    params: Record<string, number | string | boolean>;
    jobCount: number;
    machineCount: number;
    makespan: number | null;
    valid: boolean;
    timeMs: number;
    cpuMs: number;
    peakHeap: number;
    peakHeapKb?: number;
    expanded: number;
    reached: number;
    maxDepth: number;
    stoppedByLimit: boolean;
    violations: string[];
    iterations: IterationTriple[] | null;
    schedule: ScheduledOp[] | null;
}

export interface ShortlistEntry {
    runId: string;
    note: string;
}