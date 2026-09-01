import type { RunLog, ScheduledOp } from "../types";

export interface MachineLoad {
  machineId: number;
  busy: number;
  util: number;
}

export function machineLoads(run: RunLog): MachineLoad[] {
  const schedule = run.schedule ?? [];
  const makespan = run.makespan ?? scheduleMakespan(schedule);

  const busyByMachine = new Map<number, number>();
  for (const op of schedule) {
    const previous = busyByMachine.get(op.m) ?? 0;
    busyByMachine.set(op.m, previous + (op.e - op.s));
  }

  const loads: MachineLoad[] = [];
  for (const [machineId, busy] of busyByMachine) {
    const util = makespan > 0 ? busy / makespan : 0;
    loads.push({ machineId, busy, util });
  }
  loads.sort((a, b) => b.util - a.util);
  return loads;
}

export interface OccupancyGrid {
  machineIds: number[];
  binCount: number;
  binWidth: number;
  cells: number[][];
}

export function occupancyGrid(run: RunLog, binCount: number): OccupancyGrid {
  const schedule = run.schedule ?? [];
  const makespan = run.makespan ?? scheduleMakespan(schedule);
  const loads = machineLoads(run);
  const machineIds = loads.map((load) => load.machineId);

  const rowIndexByMachine = new Map<number, number>();
  for (let row = 0; row < machineIds.length; row++) {
    rowIndexByMachine.set(machineIds[row], row);
  }

  const binWidth = makespan > 0 ? makespan / binCount : 1;
  const cells: number[][] = [];
  for (let row = 0; row < machineIds.length; row++) {
    const emptyRow: number[] = [];
    for (let bin = 0; bin < binCount; bin++) {
      emptyRow.push(0);
    }
    cells.push(emptyRow);
  }

  for (const op of schedule) {
    const row = rowIndexByMachine.get(op.m);
    if (row === undefined) { continue; }
    const firstBin = Math.floor(op.s / binWidth);
    const lastBin = Math.min(binCount - 1, Math.floor((op.e - 1) / binWidth));
    for (let bin = firstBin; bin <= lastBin; bin++) {
      const binStart = bin * binWidth;
      const binEnd = binStart + binWidth;
      const overlap = Math.min(op.e, binEnd) - Math.max(op.s, binStart);
      if (overlap > 0) { cells[row][bin] += overlap / binWidth; }
    }
  }

  return { machineIds, binCount, binWidth, cells };
}

function scheduleMakespan(schedule: ScheduledOp[]): number {
  let max = 0;
  for (const op of schedule) {
    if (op.e > max) { max = op.e; }
  }
  return max;
}

export interface GroupStats {
  count: number;
  best: number;
  worst: number;
  mean: number;
  std: number;
  spread: number;
  meanRuntimeMs: number;
  invalid: number;
}

export function groupStats(runs: RunLog[]): GroupStats {
  const makespans: number[] = [];
  let runtimeSum = 0;
  let invalid = 0;
  for (const run of runs) {
    if (!run.valid || run.makespan === null) {
      invalid++;
      continue;
    }
    makespans.push(run.makespan);
    runtimeSum += run.timeMs;
  }

  if (makespans.length === 0) {
    return { count: runs.length, best: 0, worst: 0, mean: 0, std: 0, spread: 0, meanRuntimeMs: 0, invalid };
  }

  let sum = 0;
  let best = makespans[0];
  let worst = makespans[0];
  for (const value of makespans) {
    sum += value;
    if (value < best) { best = value; }
    if (value > worst) { worst = value; }
  }
  const mean = sum / makespans.length;

  let squared = 0;
  for (const value of makespans) {
    const diff = value - mean;
    squared += diff * diff;
  }
  const std = Math.sqrt(squared / makespans.length);

  return {
    count: runs.length,
    best,
    worst,
    mean,
    std,
    spread: worst - best,
    meanRuntimeMs: runtimeSum / makespans.length,
    invalid,
  };
}

export interface HistogramBin {
  from: number;
  to: number;
  count: number;
}

export function histogram(values: number[], binCount: number): HistogramBin[] {
  const bins: HistogramBin[] = [];
  if (values.length === 0) {
    return bins;
  }
  let min = values[0];
  let max = values[0];
  for (const value of values) {
    if (value < min) { min = value; }
    if (value > max) { max = value; }
  }
  if (min === max) {
    return [{ from: min, to: max, count: values.length }];
  }
  const width = (max - min) / binCount;
  for (let i = 0; i < binCount; i++) {
    bins.push({ from: min + i * width, to: min + (i + 1) * width, count: 0 });
  }
  for (const value of values) {
    let index = Math.floor((value - min) / width);
    if (index >= binCount) { index = binCount - 1; }
    bins[index].count++;
  }
  return bins;
}


export function groupBy<T>(items: T[], keyOf: (item: T) => string): Map<string, T[]> {
  const groups = new Map<string, T[]>();
  for (const item of items) {
    const key = keyOf(item);
    const existing = groups.get(key);
    if (existing === undefined) { groups.set(key, [item]); }
    else { existing.push(item); }
  }
  return groups;
}

export interface ParetoPoint {
  run: RunLog;
  runtimeMs: number;
  makespan: number;
  optimal: boolean;
}

export function paretoPoints(runs: RunLog[]): ParetoPoint[] {
  const points: ParetoPoint[] = [];
  for (const run of runs) {
    if (run.valid && run.makespan !== null) { points.push({ run, runtimeMs: run.timeMs, makespan: run.makespan, optimal: true }); }
  }
  for (let i = 0; i < points.length; i++) {
    for (let k = 0; k < points.length; k++) {
      if (i === k) { continue; }
      const dominates =
        points[k].runtimeMs <= points[i].runtimeMs &&
        points[k].makespan <= points[i].makespan &&
        (points[k].runtimeMs < points[i].runtimeMs || points[k].makespan < points[i].makespan);
      if (dominates) {
        points[i].optimal = false;
        break;
      }
    }
  }
  return points;
}
