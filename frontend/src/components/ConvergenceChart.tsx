import type { RunLog } from "../types";

const WIDTH = 760;
const HEIGHT = 220;
const PAD_LEFT = 64;
const PAD_BOTTOM = 30;
const PAD_TOP = 12;

export default function ConvergenceChart({ run }: { run: RunLog }) {
  const iterations = run.iterations;
  if (iterations === null || iterations.length === 0) {
    return <div className="hint">No convergence curve (only Tabu Search logs iterations).</div>;
  }

  let minMakespan = iterations[0][2];
  let maxMakespan = iterations[0][1];
  for (const [, current, best] of iterations) {
    if (best < minMakespan) { minMakespan = best; }
    if (current > maxMakespan) { maxMakespan = current; }
  }
  const lastIteration = iterations[iterations.length - 1][0];

  const plotWidth = WIDTH - PAD_LEFT - 10;
  const plotHeight = HEIGHT - PAD_TOP - PAD_BOTTOM;
  const xOf = (iteration: number) => PAD_LEFT + (lastIteration > 0 ? (iteration / lastIteration) * plotWidth : 0);
  const yOf = (makespan: number) => {
    const span = maxMakespan - minMakespan;
    const ratio = span > 0 ? (makespan - minMakespan) / span : 0;
    return PAD_TOP + (1 - ratio) * plotHeight;
  };

  let bestPath = "";
  let currentPath = "";
  for (let i = 0; i < iterations.length; i++) {
    const [iteration, current, best] = iterations[i];
    const command = i === 0 ? "M" : "L";
    bestPath += command + xOf(iteration).toFixed(1) + " " + yOf(best).toFixed(1) + " ";
    currentPath += command + xOf(iteration).toFixed(1) + " " + yOf(current).toFixed(1) + " ";
  }

  return (
    <div className="chart-scroll">
      <svg width={WIDTH} height={HEIGHT} className="line-chart">
        <line x1={PAD_LEFT} y1={PAD_TOP} x2={PAD_LEFT} y2={PAD_TOP + plotHeight} className="axis" />
        <line x1={PAD_LEFT} y1={PAD_TOP + plotHeight} x2={PAD_LEFT + plotWidth} y2={PAD_TOP + plotHeight} className="axis" />
        <text x={PAD_LEFT - 8} y={PAD_TOP + 4} className="axis-label" textAnchor="end">{maxMakespan.toLocaleString("de-AT")}</text>
        <text x={PAD_LEFT - 8} y={PAD_TOP + plotHeight} className="axis-label" textAnchor="end">{minMakespan.toLocaleString("de-AT")}</text>
        <text x={PAD_LEFT + plotWidth} y={HEIGHT - 8} className="axis-label" textAnchor="end">{lastIteration} iterations</text>
        <path d={currentPath} className="line-current" />
        <path d={bestPath} className="line-best" />
      </svg>
      <div className="legend-inline">
        <span className="dot best" /> best makespan
        <span className="dot current" /> current makespan
      </div>
    </div>
  );
}
