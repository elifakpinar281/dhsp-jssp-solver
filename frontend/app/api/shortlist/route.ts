import { NextResponse } from "next/server";
import { promises as fs } from "fs";
import path from "path";
import { serializeShortlist, SHORTLIST_FILE } from "@/src/data/shortlist";
import type { ShortlistEntry } from "@/src/types";

export const dynamic = "force-dynamic";

function runsDir(): string {
  const configured = process.env.RUNS_DIR;
  if (configured !== undefined && configured !== "") { return configured; }
  return path.join(process.cwd(), "..", "runs");
}

export async function POST(request: Request) {
  try {
    const body: unknown = await request.json();
    let entries: ShortlistEntry[] = [];
    if (body !== null && typeof body === "object" && "entries" in body) {
      const candidate = (body as { entries: unknown }).entries;
      if (Array.isArray(candidate)) { entries = candidate as ShortlistEntry[]; }
    }
    await fs.writeFile(path.join(runsDir(), SHORTLIST_FILE), serializeShortlist(entries), "utf8");
    return NextResponse.json({ ok: true });
  } catch (error) {
    return NextResponse.json({ error: String(error) }, { status: 500 });
  }
}
