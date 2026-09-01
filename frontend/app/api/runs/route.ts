import { NextResponse } from "next/server";
import { promises as fs } from "fs";
import path from "path";
import { toRunLog } from "@/src/data/parseRun";
import { parseShortlist, SHORTLIST_FILE } from "@/src/data/shortlist";
import type { RunLog } from "@/src/types";

export const dynamic = "force-dynamic";

function runsDir(): string {
  const configured = process.env.RUNS_DIR;
  if (configured !== undefined && configured !== "") { return configured; }
  return path.join(process.cwd(), "..", "runs");
}

export async function GET() {
  const dir = runsDir();
  try {
    const names = await fs.readdir(dir);
    const runs: RunLog[] = [];
    let shortlistText = "";
    for (const name of names) {
      if (name === SHORTLIST_FILE) {
        shortlistText = await fs.readFile(path.join(dir, name), "utf8");
        continue;
      }
      if (!name.endsWith(".json")) { continue;}
      try {
        const text = await fs.readFile(path.join(dir, name), "utf8");
        const run = toRunLog(JSON.parse(text), name.replace(/\.json$/, ""));
        if (run !== null) { runs.push(run); }
      } catch {}
    }
    runs.sort((a, b) => a.runId.localeCompare(b.runId));
    return NextResponse.json({ dir, runs, shortlist: parseShortlist(shortlistText) });
  } catch (error) {
    return NextResponse.json({ error: "Could not read runs folder at " + dir + " (" + String(error) + ")", dir }, { status: 500 });
  }
}
