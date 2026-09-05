export function algorithmLabel(algorithm: string): string {
    const map: Record<string, string> = {
        GREEDY: "Greedy", BEAM: "Beam", BEAMSTACK: "Beamstack", BULB: "BULB", TABU: "Tabu",
    };
    return map[algorithm] ?? algorithm;
}

export function paramLabel(key: string): string {
    const map: Record<string, string> = {
        beam: "Beam Width", tenure: "Tenure", noImprove: "No-Improve", heuristic: "Heuristic",
        start: "Start", nb: "Neighbourhood", kick: "Kick", time: "Time (ms)", seed: "Seed",
        randomness: "Randomness", weight: "Weight",
    };
    return map[key] ?? key;
}