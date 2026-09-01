import type { RunLog } from "../types";
import { formatMakespan } from "../format";

interface Props {
  configRuns: RunLog[];
  allRuns: RunLog[];
  selectedRunId: string | null;
  bestRunId: string | null;
  shortlistIds: Set<string>;
  shortlistOnly: boolean;
  onSelect: (runId: string) => void;
  onToggleShortlist: (runId: string) => void;
  onToggleShortlistOnly: (value: boolean) => void;
}

export default function RunPicker(props: Props) {
  const source = props.shortlistOnly
    ? props.allRuns.filter((run) => props.shortlistIds.has(run.runId))
    : props.configRuns;

  const options = [...source].sort((a, b) => (a.makespan ?? Infinity) - (b.makespan ?? Infinity));
  const starred = props.selectedRunId !== null && props.shortlistIds.has(props.selectedRunId);

  return (
    <div className="run-picker">
      <label className="section-label">Choose run from list</label>
      <div className="run-picker-row">
        <select
          className="log-dropdown"
          value={props.selectedRunId ?? ""}
          onChange={(event) => { if (event.target.value !== "") { props.onSelect(event.target.value); } }}
        >
          {options.length === 0 ? <option value="">No runs</option> : null}
          {options.map((run) => (
            <option key={run.runId} value={run.runId}>
              {optionLabel(run, run.runId === props.bestRunId)}
            </option>
          ))}
        </select>
        <button
          className={starred ? "star-button on" : "star-button"}
          title="Add / remove from shortlist"
          disabled={props.selectedRunId === null}
          onClick={() => { if (props.selectedRunId !== null) { props.onToggleShortlist(props.selectedRunId); } }}
        >
          {starred ? "★ shortlisted" : "☆ shortlist"}
        </button>
        <label className="checkbox">
          <input type="checkbox" checked={props.shortlistOnly} onChange={(event) => props.onToggleShortlistOnly(event.target.checked)} />
          shortlist only
        </label>
      </div>
    </div>
  );
}

function optionLabel(run: RunLog, isBest: boolean): string {
  const seed = run.params.seed;
  const seedPart = seed !== undefined ? "seed " + String(seed) + " · " : "";
  const bestPart = isBest ? "  ★ best" : "";
  return seedPart + formatMakespan(run.makespan) + bestPart + "  ·  " + run.runId;
}
