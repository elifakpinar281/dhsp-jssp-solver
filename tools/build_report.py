#!/usr/bin/env python3

import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)

HTML_PATH = os.path.join(HERE, "anlagen-scheduler.html")
MAPPING_PATH = os.path.join(HERE, "demoanlage_mapping.json")


def find_results_path(argv):
    if len(argv) > 1:
        return argv[1]
    candidates = [
        os.path.join(os.getcwd(), "results.json"),
        os.path.join(PROJECT, "results.json"),
        os.path.join(HERE, "results.json"),
    ]
    for c in candidates:
        if os.path.exists(c):
            return c
    raise SystemExit(
        "Keine results.json gefunden. Erst RunAll laufen lassen, oder Pfad angeben:\n""    python3 tools/build_report.py pfad/zu/results.json"
    )


def load_mapping():
    if not os.path.exists(MAPPING_PATH):
        return None
    with open(MAPPING_PATH, encoding="utf-8") as f:
        return json.load(f)


def enrich(data, mapping):
    instances = data.get("instances", {})
    for key, inst in instances.items():
        machine_count = inst.get("machineCount", 0)
        job_count = inst.get("jobCount", 0)

        if key == "demoanlage" and mapping is not None:
            inst["machineNames"] = mapping.get("machineNames", {})
            inst["jobTypeOfJob"] = mapping.get("jobTypeOfJob", list(range(job_count)))
            inst["jobtypeNames"] = mapping.get("jobtypeNames", {})
            inst["jobtypeCounts"] = mapping.get("jobtypeCounts", {})
        else:
            # generische Namen fuer ft06 / la02
            inst["machineNames"] = {str(i): f"Maschine {i}" for i in range(machine_count)}
            inst["jobTypeOfJob"] = list(range(job_count))
    return data


def inject(html, data):
    payload = json.dumps(data, ensure_ascii=False)
    new_line = f"    const DATA = {payload};"
    pattern = re.compile(r"^\s*const DATA = .*;\s*$", re.MULTILINE)
    if not pattern.search(html):
        raise SystemExit("In der HTML wurde keine Zeile 'const DATA = ...;' gefunden.")
    return pattern.sub(lambda _: new_line, html, count=1)


def main():
    results_path = find_results_path(sys.argv)
    with open(results_path, encoding="utf-8") as f:
        data = json.load(f)

    mapping = load_mapping()
    if mapping is None:
        print("Hinweis: demoanlage_mapping.json nicht gefunden - Demoanlage bekommt generische Namen.")

    data = enrich(data, mapping)

    with open(HTML_PATH, encoding="utf-8") as f:
        html = f.read()

    html = inject(html, data)

    with open(HTML_PATH, "w", encoding="utf-8") as f:
        f.write(html)

    n = len(data.get("instances", {}))
    print(f"OK: {n} Instanz(en) aus {os.path.relpath(results_path, PROJECT)} in die HTML eingespielt.")
    print(f"Jetzt oeffnen: {os.path.relpath(HTML_PATH, PROJECT)} (Doppelklick im Datei-Explorer)")


if __name__ == "__main__":
    main()
