import type { RunLog } from "../types";
import { paretoPoints } from "../analysis/metrics";
import { algorithmColor } from "../colors";

const WIDTH = 720;
const HEIGHT = 260;
const PAD_LEFT = 64;
const PAD_BOTTOM = 40;
const PAD_TOP = 12;
const PAD_RIGHT = 12;

export default function ParetoScatter({ runs }: { runs: RunLog[] }) {
    const points = paretoPoints(runs);
    if (points.length === 0) {
        return <div className="hint">No valid runs for the trade-off plot.</div>;
    }

    const fjssp = runs.length > 0 && runs[0].mode === "FJSSP";
    let maxRuntime = 1;
    let minMakespan = points[0].makespan;
    let maxMakespan = points[0].makespan;
    for (const point of points) {
        if (point.runtimeMs > maxRuntime) { maxRuntime = point.runtimeMs; }
        if (point.makespan < minMakespan) { minMakespan = point.makespan; }
        if (point.makespan > maxMakespan) { maxMakespan = point.makespan; }
    }

    const plotWidth = WIDTH - PAD_LEFT - PAD_RIGHT;
    const plotHeight = HEIGHT - PAD_TOP - PAD_BOTTOM;
    const xOf = (runtimeMs: number) => PAD_LEFT + (runtimeMs / maxRuntime) * plotWidth;
    const yOf = (makespan: number) => {
        const span = maxMakespan - minMakespan;
        const ratio = span > 0 ? (makespan - minMakespan) / span : 0;
        return PAD_TOP + ratio * plotHeight;
    };

    const algorithms: string[] = [];
    for (const point of points) {
        if (!algorithms.includes(point.run.algorithm)) {
            algorithms.push(point.run.algorithm);
        }
    }

    return (
        <div className="chart-scroll">
            <svg width={WIDTH} height={HEIGHT} className="scatter">
                <line x1={PAD_LEFT} y1={PAD_TOP} x2={PAD_LEFT} y2={PAD_TOP + plotHeight} className="axis" />
                <line x1={PAD_LEFT} y1={PAD_TOP + plotHeight} x2={PAD_LEFT + plotWidth} y2={PAD_TOP + plotHeight} className="axis" />
                <text x={PAD_LEFT - 8} y={PAD_TOP + 4} className="axis-label" textAnchor="end">{maxMakespan.toLocaleString("de-AT")}</text>
                <text x={PAD_LEFT - 8} y={PAD_TOP + plotHeight} className="axis-label" textAnchor="end">{minMakespan.toLocaleString("de-AT")}</text>
                <text x={PAD_LEFT} y={HEIGHT - 8} className="axis-label" textAnchor="start">0 ms</text>
                <text x={PAD_LEFT + plotWidth} y={HEIGHT - 8} className="axis-label" textAnchor="end">{Math.round(maxRuntime).toLocaleString("de-AT")} ms</text>
                <text x={PAD_LEFT + plotWidth / 2} y={HEIGHT - 8} className="axis-label" textAnchor="middle">Runtime →</text>
                {points.map((point, index) => (
                    <circle
                        key={index}
                        cx={xOf(point.runtimeMs)}
                        cy={yOf(point.makespan)}
                        r={point.optimal ? 6 : 4}
                        fill={algorithmColor(point.run.algorithm, fjssp)}
                        stroke={point.optimal ? "#111827" : "none"}
                        strokeWidth={point.optimal ? 1.5 : 0}
                        opacity={0.85}
                    >
                        <title>{point.run.runId + "\nMakespan " + point.makespan + " s · " + point.runtimeMs + " ms" + (point.optimal ? " · Pareto-optimal" : "")}</title>
                    </circle>
                ))}
            </svg>
            <div className="legend-inline">
                {algorithms.map((algorithm) => (
                    <span key={algorithm} className="legend-key">
            <span className="dot" style={{ background: algorithmColor(algorithm, fjssp) }} />{algorithm}
          </span>
                ))}
                <span className="legend-key"><span className="dot ring" />Pareto-optimal</span>
            </div>
        </div>
    );
}