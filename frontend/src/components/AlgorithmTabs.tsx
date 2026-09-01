import { algorithmLabel } from "../labels";

interface Props {
  algorithms: string[];
  selected: string;
  onSelect: (algorithm: string) => void;
}

export default function AlgorithmTabs({ algorithms, selected, onSelect }: Props) {
  return (
    <div className="tabs">
      {algorithms.map((algorithm) => (
        <button key={algorithm} className={algorithm === selected ? "tab active" : "tab"} onClick={() => onSelect(algorithm)}>
          {algorithmLabel(algorithm)}
        </button>
      ))}
    </div>
  );
}
