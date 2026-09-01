import type { RunLog } from "../types";
import { distinctParamValues, nonSeedParamKeys } from "../analysis/grouping";
import { paramLabel } from "../labels";

interface Props {
  algorithmRuns: RunLog[];
  selected: Record<string, string>;
  onChange: (key: string, value: string) => void;
}

export default function ParameterBar({ algorithmRuns, selected, onChange }: Props) {
  const keys = nonSeedParamKeys(algorithmRuns);
  if (keys.length === 0) {
    return <div className="param-empty">No tunable parameters for this algorithm.</div>;
  }

  return (
    <div className="param-groups">
      {keys.map((key) => (
        <div className="param-group" key={key}>
          <span className="param-name">{paramLabel(key)}</span>
          <div className="param-values">
            {distinctParamValues(algorithmRuns, key).map((value) => (
              <button key={value} className={selected[key] === value ? "param-value active" : "param-value"} onClick={() => onChange(key, value)}>
                {value}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
