import type { RunLog } from "../types";
import type { GroupStats } from "../analysis/metrics";
import { distinctParamValues } from "../analysis/grouping";
import { groupStats } from "../analysis/metrics";
import { formatInt, formatRuntime } from "../format";

interface Props {
    algorithmRuns: RunLog[];
    selected: Record<string, string>;
    fjssp: boolean;
}

interface Box {
    min: number;
    q1: number;
    median: number;
    q3: number;
    max: number;
}

interface Row {
    randomness: string;
    makespans: number[];
    box: Box;
    stats: GroupStats;
}

const BOX = "#e11d48";
const MEDIAN = "#881337";
const BEST = "#f43f5e";
const WHISKER = "#cf9aa8";
const GRID = "#f6e5ea";
const AXIS = "#a68b95";

function formatPercent(raw: string): string {
    const value = Number(raw);
    if (Number.isNaN(value)) { return raw; }
    return Math.round(value * 100) + "%";
}

function medianOf(sorted: number[], from: number, to: number): number {
    const length = to - from;
    const mid = from + Math.floor(length / 2);
    if (length % 2 === 1) { return sorted[mid]; }
    return (sorted[mid - 1] + sorted[mid]) / 2;
}

function quartiles(values: number[]): Box {
    const sorted = values.slice().sort((a, b) => a - b);
    const n = sorted.length;

    if (n === 1) {
        const only = sorted[0];
        return { min: only, q1: only, median: only, q3: only, max: only };
    }
    const median = medianOf(sorted, 0, n);
    const lowerTo = Math.floor(n / 2);
    const upperFrom = n % 2 === 0 ? n / 2 : Math.floor(n / 2) + 1;
    const q1 = medianOf(sorted, 0, lowerTo);
    const q3 = medianOf(sorted, upperFrom, n);
    return { min: sorted[0], q1, median, q3, max: sorted[n - 1] };
}

function matchesHeld(run: RunLog, selected: Record<string, string>): boolean {
    for (const key of Object.keys(selected)) {
        if (key === "randomness" || key === "seed") { continue; }
        if (String(run.params[key]) !== selected[key]) { return false; }
    }
    return true;
}

export default function RandomnessComparison({ algorithmRuns, selected }: Props) {
    const held: RunLog[] = [];
    for (const run of algorithmRuns) {
        if (matchesHeld(run, selected)) { held.push(run); }
    }

    const values = distinctParamValues(held, "randomness");
    if (values.length < 2) {
        return (
            <div className="hint">
                For a randomness comparison, run several levels (e.g. 0, 0.05, 0.1, 0.2, 0.3)
                and keep the other parameters fixed.
            </div>
        );
    }

    const rows: Row[] = [];
    for (const value of values) {
        const makespans: number[] = [];
        const matching: RunLog[] = [];
        for (const run of held) {
            if (String(run.params["randomness"]) !== value) { continue; }
            matching.push(run);
            if (run.valid && run.makespan !== null) { makespans.push(run.makespan); }
        }
        if (makespans.length === 0) { continue; }
        rows.push({
            randomness: value,
            makespans,
            box: quartiles(makespans),
            stats: groupStats(matching),
        });
    }

    if (rows.length < 2) {
        return <div className="hint">No valid runs for this comparison.</div>;
    }

    let yMin = Number.POSITIVE_INFINITY;
    let yMax = Number.NEGATIVE_INFINITY;
    for (const row of rows) {
        for (const value of row.makespans) {
            if (value < yMin) { yMin = value; }
            if (value > yMax) { yMax = value; }
        }
    }
    if (yMin === yMax) { yMin -= 1; yMax += 1; }
    const padding = (yMax - yMin) * 0.08;
    yMin -= padding;
    yMax += padding;

    const width = 900;
    const height = 340;
    const marginLeft = 60;
    const marginRight = 16;
    const marginTop = 16;
    const marginBottom = 40;
    const plotWidth = width - marginLeft - marginRight;
    const plotHeight = height - marginTop - marginBottom;
    const slotWidth = plotWidth / rows.length;
    const boxWidth = Math.min(slotWidth * 0.5, 90);

    function xCenter(index: number): number {
        return marginLeft + slotWidth * (index + 0.5);
    }
    function yAt(value: number): number {
        return marginTop + plotHeight * (1 - (value - yMin) / (yMax - yMin));
    }

    const ticks: number[] = [];
    for (let t = 0; t <= 4; t++) {
        ticks.push(yMin + ((yMax - yMin) * t) / 4);
    }

    let bestLine = "";
    for (let i = 0; i < rows.length; i++) {
        bestLine += (i === 0 ? "M" : "L") + xCenter(i) + " " + yAt(rows[i].box.min);
    }

    return (
        <div>
            <svg viewBox={"0 0 " + width + " " + height} width="100%" role="img" aria-label="Makespan distribution across randomness levels">
                {ticks.map((value, i) => (
                    <g key={"tick-" + i}>
                        <line x1={marginLeft} y1={yAt(value)} x2={width - marginRight} y2={yAt(value)} stroke={GRID} strokeWidth={1} />
                        <text x={marginLeft - 8} y={yAt(value) + 4} textAnchor="end" fontSize={11} fill={AXIS}>
                            {formatInt(Math.round(value))}
                        </text>
                    </g>
                ))}

                {rows.map((row, i) => {
                    const cx = xCenter(i);
                    const left = cx - boxWidth / 2;
                    const yQ1 = yAt(row.box.q1);
                    const yQ3 = yAt(row.box.q3);
                    const boxTop = Math.min(yQ1, yQ3);
                    const boxHeight = Math.max(1, Math.abs(yQ1 - yQ3));

                    return (
                        <g key={"box-" + row.randomness}>
                            <line x1={cx} y1={yAt(row.box.min)} x2={cx} y2={yAt(row.box.max)} stroke={WHISKER} strokeWidth={1.2} />
                            <rect x={left} y={boxTop} width={boxWidth} height={boxHeight} fill={BOX} fillOpacity={0.14} stroke={BOX} strokeWidth={1.4} />
                            <line x1={left} y1={yAt(row.box.median)} x2={left + boxWidth} y2={yAt(row.box.median)} stroke={MEDIAN} strokeWidth={2} />
                            {row.makespans.map((value, k) => {
                                const offset = (k - (row.makespans.length - 1) / 2) * (boxWidth * 0.12);
                                return <circle key={"pt-" + i + "-" + k} cx={cx + offset} cy={yAt(value)} r={2.2} fill={BOX} fillOpacity={0.55} />;
                            })}
                            <text x={cx} y={height - marginBottom + 20} textAnchor="middle" fontSize={11} fill={AXIS}>
                                {formatPercent(row.randomness)}
                            </text>
                        </g>
                    );
                })}

                <path d={bestLine} fill="none" stroke={BEST} strokeWidth={1.6} strokeDasharray="4 3" />

                <text
                    x={16}
                    y={marginTop + plotHeight / 2}
                    textAnchor="middle"
                    fontSize={11}
                    fill={AXIS}
                    transform={"rotate(-90 16 " + (marginTop + plotHeight / 2) + ")"}
                >
                    Makespan (s)
                </text>
            </svg>

            <div className="hint" style={{ marginTop: 4 }}>
                Box = Q1–Q3, line = median, dots = individual seeds, dashed = best per level.
            </div>

            <table className="data-table" style={{ marginTop: 12 }}>
                <thead>
                <tr>
                    <th>Randomness</th>
                    <th>Runs</th>
                    <th>Mean</th>
                    <th>Best</th>
                    <th>Worst</th>
                    <th>Std</th>
                    <th>Mean Runtime</th>
                </tr>
                </thead>
                <tbody>
                {rows.map((row) => (
                    <tr key={row.randomness}>
                        <td className="mono">{formatPercent(row.randomness)}</td>
                        <td>{row.stats.count}</td>
                        <td>{formatInt(Math.round(row.stats.mean))} s</td>
                        <td>{formatInt(row.stats.best)} s</td>
                        <td>{formatInt(row.stats.worst)} s</td>
                        <td>{row.stats.std.toFixed(1)}</td>
                        <td>{formatRuntime(Math.round(row.stats.meanRuntimeMs))}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
    );
}