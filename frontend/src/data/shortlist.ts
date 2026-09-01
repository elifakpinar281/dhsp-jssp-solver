import type { ShortlistEntry } from "../types";

export const SHORTLIST_FILE = "shortlist.txt";

export function parseShortlist(text: string): ShortlistEntry[] {
  const entries: ShortlistEntry[] = [];
  const lines = text.split(/\r?\n/);
  for (const line of lines) {
    const hashIndex = line.indexOf("#");
    const idPart = (hashIndex >= 0 ? line.slice(0, hashIndex) : line).trim();
    if (idPart === "") { continue; }
    const note = hashIndex >= 0 ? line.slice(hashIndex + 1).trim() : "";
    entries.push({ runId: idPart, note });
  }
  return entries;
}

export function serializeShortlist(entries: ShortlistEntry[]): string {
  const lines: string[] = [
    "# Shortlist: ",
    "",
  ];
  for (const entry of entries) {
    lines.push(entry.note === "" ? entry.runId : entry.runId + "  # " + entry.note);
  }
  return lines.join("\n") + "\n";
}
