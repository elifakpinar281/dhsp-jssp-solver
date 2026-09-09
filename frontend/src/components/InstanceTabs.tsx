interface InstanceOption {
    key: string;
    jobCount: number;
    runCount: number;
}

interface Props {
    instances: InstanceOption[];
    selected: string;
    onSelect: (key: string) => void;
}

export default function InstanceTabs({ instances, selected, onSelect }: Props) {
    return (
        <div className="tabs">
            {instances.map((instance) => (
                <button
                    key={instance.key}
                    className={instance.key === selected ? "tab active" : "tab"}
                    onClick={() => onSelect(instance.key)}
                >
                    {instance.jobCount} Jobs ({instance.runCount})
                </button>
            ))}
        </div>
    );
}