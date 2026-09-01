const JOB_COLORS = ["#7c5cff", "#22c55e", "#f472b6", "#f6d55c", "#38bdf8", "#fb923c", "#a78bfa", "#34d399", "#f87171", "#60a5fa", "#c084fc", "#4ade80",];

export function jobColor(jobId: number): string {
  return JOB_COLORS[((jobId % JOB_COLORS.length) + JOB_COLORS.length) % JOB_COLORS.length];
}

const ALGO_COLORS: Record<string, string> = {
  GREEDY: "#fb923c",
  BEAM: "#7c5cff",
  BEAMSTACK: "#38bdf8",
  BULB: "#22c55e",
  TABU: "#f472b6",
};

export function algorithmColor(algorithm: string): string {
  return ALGO_COLORS[algorithm] ?? "#94a3b8";
}

export function utilizationColor(util: number): string {
  const clamped = Math.max(0, Math.min(1, util));
  const hue = 120 * (1 - clamped);
  return "hsl(" + Math.round(hue) + ", 70%, 45%)";
}

export function heatColor(value: number): string {
  const clamped = Math.max(0, Math.min(1, value));
  const from = { r: 245, g: 243, b: 255 };
  const to = { r: 76, g: 29, b: 149 };
  const r = Math.round(from.r + (to.r - from.r) * clamped);
  const g = Math.round(from.g + (to.g - from.g) * clamped);
  const b = Math.round(from.b + (to.b - from.b) * clamped);
  return "rgb(" + r + ", " + g + ", " + b + ")";
}
