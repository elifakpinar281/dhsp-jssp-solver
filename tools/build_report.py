#!/usr/bin/env python3

import glob
import json
import os
import re
import statistics
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)

TEMPLATE_PATH = os.path.join(HERE, "anlagen-scheduler.html")
OUTPUT_PATH = os.path.join(HERE, "report.html")
MAPPING_PATH = os.path.join(HERE, "demoanlage_mapping.json")

LABELS = {
    "demoanlage": "Demoanlage (IM1)",
    "ft06": "FT06 (6x6)",
    "la02": "LA02 (10x5)",
    "ea01": "EA01",
}


def label_for(key, jobs, machines):
    return LABELS.get(key, f"{key.upper()} ({jobs}x{machines})")


def load_runs(runs_dir):
    paths = sorted(glob.glob(os.path.join(runs_dir, "*.json")))
    if not paths:
        raise SystemExit(
            f"Keine Logs in {os.path.relpath(runs_dir, PROJECT)} gefunden. Erst Laeufe starten:\n"
            '    ./gradlew run --args="run --algo GREEDY,BEAM,TABU --instance benchmarks/ft06.txt"'
        )
    runs = []
    for path in paths:
        with open(path, encoding="utf-8") as f:
            runs.append(json.load(f))
    return runs


def by_instance(runs):
    grouped = {}
    for run in runs:
        grouped.setdefault(run["instanceKey"], []).append(run)
    return grouped


def is_better(run, best):
    if best is None:
        return True
    if run["makespan"] is None:
        return False
    if best["makespan"] is None:
        return True
    if run["valid"] != best["valid"]:
        return run["valid"]
    return run["makespan"] < best["makespan"]


def best_per_algorithm(runs):
    best = {}
    for run in runs:
        algo = run["algorithm"]
        if is_better(run, best.get(algo)):
            best[algo] = run
    return best


def to_result(run):
    return {
        "runId": run["runId"],
        "timeMs": run["timeMs"],
        "expanded": run["expanded"],
        "reached": run["reached"],
        "maxDepth": run["maxDepth"],
        "peakHeap": run["peakHeap"],
        "iterations": run.get("iterations"),
        "valid": run["valid"],
        "makespan": run["makespan"],
        "schedule": run["schedule"],
    }


def build_data(grouped, mapping):
    instances = {}
    for key, runs in grouped.items():
        first = runs[0]
        jobs, machines = first["jobCount"], first["machineCount"]

        results = {}
        for algo, run in best_per_algorithm(runs).items():
            results[algo] = to_result(run)

        inst = {
            "label": label_for(key, jobs, machines),
            "jobCount": jobs,
            "machineCount": machines,
            "results": results,
        }

        if key == "demoanlage" and mapping is not None:
            inst["machineNames"] = mapping.get("machineNames", {})
            inst["jobTypeOfJob"] = mapping.get("jobTypeOfJob", list(range(jobs)))
            inst["jobtypeNames"] = mapping.get("jobtypeNames", {})
            inst["jobtypeCounts"] = mapping.get("jobtypeCounts", {})
        else:
            inst["machineNames"] = {str(i): f"Maschine {i}" for i in range(machines)}
            inst["jobTypeOfJob"] = list(range(jobs))

        instances[key] = inst
    return {"instances": instances}


def build_tabu_stats(grouped):
    stats = {}
    for key, runs in grouped.items():
        tabu = [r for r in runs if r["algorithm"] == "TABU"]
        if not tabu:
            continue
        tabu.sort(key=lambda r: r["params"].get("seed", 0))

        entries = []
        for run in tabu:
            entries.append({
                "runId": run["runId"],
                "seed": run["params"].get("seed"),
                "makespan": run["makespan"],
                "timeMs": run["timeMs"],
                "valid": run["valid"],
                # Das Frontend zeigt hier die Anzahl der Iterationen, nicht den Verlauf.
                "iterations": len(run["iterations"]) if run.get("iterations") else 0,
                "schedule": run["schedule"],
            })

        makespans = [e["makespan"] for e in entries if e["makespan"] is not None]
        times = [e["timeMs"] for e in entries]
        summary = {
            "min": min(makespans) if makespans else None,
            "max": max(makespans) if makespans else None,
            "mean": round(statistics.fmean(makespans), 1) if makespans else None,
            "median": round(statistics.median(makespans), 1) if makespans else None,
            "stdDev": round(statistics.pstdev(makespans), 2) if len(makespans) > 1 else 0.0,
            "meanTimeMs": round(statistics.fmean(times), 1) if times else 0.0,
            "invalidCount": sum(1 for e in entries if not e["valid"]),
        }

        first = grouped[key][0]
        stats[key] = {
            "label": label_for(key, first["jobCount"], first["machineCount"]),
            "runCount": len(entries),
            "summary": summary,
            "runs": entries,
        }
    return stats


def build_beam_sweep(grouped):
    sweep = {}
    for key, runs in grouped.items():
        beam = [r for r in runs if r["algorithm"] == "BEAM"]
        if not beam:
            continue

        best_by_k = {}
        for run in beam:
            k = run["params"].get("beam")
            if is_better(run, best_by_k.get(k)):
                best_by_k[k] = run

        points = []
        for k in sorted(best_by_k):
            run = best_by_k[k]
            points.append({
                "runId": run["runId"],
                "k": k,
                "makespan": run["makespan"],
                "timeMs": run["timeMs"],
                "expanded": run["expanded"],
                "maxDepth": run["maxDepth"],
                "valid": run["valid"],
            })

        first = grouped[key][0]
        sweep[key] = {
            "label": label_for(key, first["jobCount"], first["machineCount"]),
            "points": points,
        }
    return sweep


def inject(html, name, payload):
    line = f"    const {name} = {json.dumps(payload, ensure_ascii=False)};"
    pattern = re.compile(rf"^\s*const {name} = .*;\s*$", re.MULTILINE)
    if not pattern.search(html):
        raise SystemExit(f"In der HTML wurde keine Zeile 'const {name} = ...;' gefunden.")
    return pattern.sub(lambda _: line, html, count=1)


def main():
    runs_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(PROJECT, "runs")
    runs = load_runs(runs_dir)
    grouped = by_instance(runs)

    mapping = None
    if os.path.exists(MAPPING_PATH):
        with open(MAPPING_PATH, encoding="utf-8") as f:
            mapping = json.load(f)
    else:
        print("Hinweis: demoanlage_mapping.json nicht gefunden - Demoanlage bekommt generische Namen.")

    with open(TEMPLATE_PATH, encoding="utf-8") as f:
        html = f.read()

    html = inject(html, "DATA", build_data(grouped, mapping))
    html = inject(html, "TABU_STATS", build_tabu_stats(grouped))
    html = inject(html, "BEAM_SWEEP", build_beam_sweep(grouped))

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        f.write(html)

    print(f"OK: {len(runs)} Log(s), {len(grouped)} Instanz(en) eingespielt.")
    for key, instance_runs in sorted(grouped.items()):
        algos = sorted({r["algorithm"] for r in instance_runs})
        print(f"  {key}: {len(instance_runs)} Laeufe ({', '.join(algos)})")
    print(f"Jetzt oeffnen: {os.path.relpath(OUTPUT_PATH, PROJECT)}")


if __name__ == "__main__":
    main()
