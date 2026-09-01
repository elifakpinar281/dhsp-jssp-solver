import type { RunLog } from "../types";

function paramsToString(params: Record<string, number | string | boolean>, skipSeed: boolean): string {
  const keys = Object.keys(params);
  keys.sort();
  const parts: string[] = [];
  for (const key of keys) {
    if (skipSeed && key === "seed") { continue; }
    parts.push(key + "=" + String(params[key]));
  }
  return parts.join(" ");
}

export function configLabel(run: RunLog): string {
  const params = paramsToString(run.params, false);
  return params === "" ? run.algorithm : run.algorithm + " " + params;
}

export function configKey(run: RunLog): string {
  const params = paramsToString(run.params, true);
  return params === "" ? run.algorithm : run.algorithm + " " + params;
}

export function memoryKb(run: RunLog): number {
  return run.peakHeapKb !== undefined ? run.peakHeapKb : run.peakHeap * 1024;
}

export function nonSeedParamKeys(runs: RunLog[]): string[] {
  const values = new Map<string, Set<string>>();
  for (const run of runs) {
    for (const key of Object.keys(run.params)) {
      if (key === "seed") { continue; }
      const set = values.get(key) ?? new Set<string>();
      set.add(String(run.params[key]));
      values.set(key, set);
    }
  }
  const keys: string[] = [];
  for (const [key, set] of values) {
    if (set.size > 1) { keys.push(key); }
  }
  keys.sort();
  return keys;
}

export function distinctParamValues(runs: RunLog[], key: string): string[] {
  const set = new Set<string>();
  for (const run of runs) {
    const value = run.params[key];
    if (value !== undefined) { set.add(String(value)); }
  }
  const values: string[] = [];
  for (const value of set) { values.push(value); }
  values.sort((a, b) => {
    const na = Number(a);
    const nb = Number(b);
    if (!Number.isNaN(na) && !Number.isNaN(nb)) { return na - nb; }
    return a.localeCompare(b);
  });
  return values;
}

export function primaryParamKey(runs: RunLog[]): string | null {
  let best: string | null = null;
  let bestCount = 1;
  for (const key of nonSeedParamKeys(runs)) {
    const count = distinctParamValues(runs, key).length;
    if (count > bestCount) {
      bestCount = count;
      best = key;
    }
  }
  return best;
}

export function bestRun(runs: RunLog[]): RunLog | null {
  let best: RunLog | null = null;
  for (const run of runs) {
    if (run.valid && run.makespan !== null) {
      if (best === null || run.makespan < (best.makespan ?? Number.POSITIVE_INFINITY)) { best = run;}
    }
  }
  return best;
}
