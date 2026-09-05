import type { RunLog } from "../types";
import type { GroupStats } from "../analysis/metrics";
import { distinctParamValues } from "../analysis/grouping";
import { groupStats } from "../analysis/metrics";
import { algorithmColor } from "../colors";
import { formatInt, formatRuntime } from "../format";

interface Props {
    algorithmRuns: RunLog[];
    selected: Record<string, string>;
    fjssp: boolean;
}

interface Row {
    randomness: string;
    stats: GroupStats;
}

function formatPercent(raw: string): string {
    const value = Number(raw);
    if (Number.isNaN(value)) { return raw; }
    return Math.round(value * 100) + "%";
}

function matchesHeld(run: RunLog, selected: Record<string, string>): boolean {
    for (const key of Object.keys(selected)) {
        if (key === "randomness" || key === "seed") { continue; }
        if (String(run.params[key]) !== selected[key]) { return false; }
    }
    return true;
}

export default function RandomnessComparison({ algorithmRuns, selected, fjssp }: Props) {
    const held: RunLog[] = [];
    for (const run of algorithmRuns) {
        if (matchesHeld(run, selected)) { held.push(run); }
    }

    const values = distinctParamValues(held, "randomness");
    if (values.length < 2) {
        return (
            <div className="hint">
                Fuer einen Randomness-Vergleich mehrere Stufen rechnen (z. B. 0, 0.05, 0.1, 0.2, 0.3)
                und die uebrigen Parameter fix halten.
            </div>
        );
    }

    const rows: Row[] = [];
    for (const value of values) {
        const matching: RunLog[] = [];
        for (const run of held) {
            if (String(run.params["randomness"]) === value) { matching.push(run); }
        }
        rows.push({ randomness: value, stats: groupStats(matching) });
    }

    let yMin = Number.POSITIVE_INFINITY;
    let yMax = Number.NEGATIVE_INFINITY;
    for (const row of rows) {
        if (row.stats.count > 0 && row.stats.best < yMin) { yMin = row.stats.best; }
        if (row.stats.count > 0 && row.stats.worst > yMax) { yMax = row.stats.worst; }
    }
    if (!Number.isFinite(yMin) || !Number.isFinite(yMax)) {
        return <div className="hint">Keine gueltigen Laeufe fuer den Vergleich.</div>;
    }
    if (yMin === yMax) { yMin -= 1; yMax += 1; }
    const padding = (yMax - yMin) * 0.1;
    yMin -= padding;
    yMax += padding;

    const width = 640;
    const height = 300;
    const marginLeft = 60;
    const marginRight = 16;
    const marginTop = 16;
    const marginBottom = 40;
    const plotWidth = width - marginLeft - marginRight;
    const plotHeight = height - marginTop - marginBottom;

    function xAt(index: number): number {
        if (rows.length === 1) { return marginLeft + plotWidth / 2; }
        return marginLeft + (plotWidth * index) / (rows.length - 1);
    }
    function yAt(value: number): number {
        return marginTop + plotHeight * (1 - (value - yMin) / (yMax - yMin));
    }

    const algorithm = algorithmRuns.length > 0 ? algorithmRuns[0].algorithm : "";
    const color = algorithmColor(algorithm, fjssp);

    let band = "";
    for (let i = 0; i < rows.length; i++) {
        band += (i === 0 ? "" : " ") + xAt(i) + "," + yAt(rows[i].stats.worst);
    }
    for (let i = rows.length - 1; i >= 0; i--) {
        band += " " + xAt(i) + "," + yAt(rows[i].stats.best);
    }

    let meanLine = "";
    for (let i = 0; i < rows.length; i++) {
        meanLine += (i === 0 ? "M" : "L") + xAt(i) + " " + yAt(rows[i].stats.mean);
    }

    const ticks: number[] = [];
    for (let t = 0; t <= 4; t++) {
        ticks.push(yMin + ((yMax - yMin) * t) / 4);
    }

    const axisColor = "#94a3b8";
    const gridColor = "#e2e8f0";

    return (
        <div>
            <svg viewBox={"0 0 " + width + " " + height} width="100%" role="img" aria-label="Makespan ueber Randomness">
                {ticks.map((value, i) => (
                    <g key={"tick-" + i}>
                        <line x1={marginLeft} y1={yAt(value)} x2={width - marginRight} y2={yAt(value)} stroke={gridColor} strokeWidth={1} />
                        <text x={marginLeft - 8} y={yAt(value) + 4} textAnchor="end" fontSize={11} fill={axisColor}>
                            {formatInt(Math.round(value))}
                        </text>
                    </g>
                ))}

                <polygon points={band} fill={color} fillOpacity={0.15} stroke="none" />
                <path d={meanLine} fill="none" stroke={color} strokeWidth={2} />

                {rows.map((row, i) => (
                    <g key={"pt-" + row.randomness}>
                        <circle cx={xAt(i)} cy={yAt(row.stats.mean)} r={3.5} fill={color} />
                        <text x={xAt(i)} y={height - marginBottom + 20} textAnchor="middle" fontSize={11} fill={axisColor}>
                            {formatPercent(row.randomness)}
                        </text>
                    </g>
                ))}

                <text
                    x={16}
                    y={marginTop + plotHeight / 2}
                    textAnchor="middle"
                    fontSize={11}
                    fill={axisColor}
                    transform={"rotate(-90 16 " + (marginTop + plotHeight / 2) + ")"}
                >
                    Makespan (s)
                </text>
            </svg>

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