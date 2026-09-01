export function formatRuntime(timeMs: number): string {
  if (timeMs < 1000) { return timeMs + " ms"; }
  const seconds = timeMs / 1000;
  return seconds.toFixed(seconds < 10 ? 1 : 0) + " s";
}

export function formatMemory(peakHeapKb: number | undefined, peakHeapMb: number): string {
  const kb = peakHeapKb !== undefined ? peakHeapKb : peakHeapMb * 1024;
  if (kb < 1024) { return kb + " KB";}
  return (kb / 1024).toFixed(1) + " MB";
}

export function formatInt(value: number): string {
  return value.toLocaleString("de-AT");
}

export function formatMakespan(makespan: number | null): string {
  if (makespan === null) { return "—"; }
  return formatInt(makespan) + " s";
}
