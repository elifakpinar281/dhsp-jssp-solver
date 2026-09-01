import type { RunLog, ShortlistEntry } from "../types";

export interface RunsResponse {
  dir: string;
  runs: RunLog[];
  shortlist: ShortlistEntry[];
}

export async function fetchRuns(): Promise<RunsResponse> {
  const response = await fetch("/api/runs", { cache: "no-store" });
  const data: unknown = await response.json();
  if (!response.ok) {
    const message = data !== null && typeof data === "object" && "error" in data ? String((data as { error: unknown }).error) : "HTTP " + response.status;
    throw new Error(message);
  }
  return data as RunsResponse;
}

export async function saveShortlist(entries: ShortlistEntry[]): Promise<void> {
  await fetch("/api/shortlist", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ entries }),
  });
}
