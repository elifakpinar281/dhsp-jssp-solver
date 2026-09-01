"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { RunLog, ShortlistEntry } from "./types";
import { fetchRuns, saveShortlist } from "./data/api";
import { bestRun } from "./analysis/grouping";
import { algorithmLabel } from "./labels";
import AlgorithmTabs from "./components/AlgorithmTabs";
import ParameterBar from "./components/ParameterBar";
import RunPicker from "./components/RunPicker";
import KpiCards from "./components/KpiCards";
import GanttChart from "./components/GanttChart";
import StatsBar from "./components/StatsBar";
import ComparisonTable from "./components/ComparisonTable";
import UtilizationHeatmap from "./components/UtilizationHeatmap";
import ConvergenceChart from "./components/ConvergenceChart";
import ParetoScatter from "./components/ParetoScatter";

const ALGORITHM_ORDER = ["GREEDY", "BEAM", "BEAMSTACK", "BULB", "TABU"];

function paramsOf(run: RunLog): Record<string, string> {
  const out: Record<string, string> = {};
  for (const key of Object.keys(run.params)) {
    if (key !== "seed") { out[key] = String(run.params[key]); }
  }
  return out;
}

function matchesParams(run: RunLog, params: Record<string, string>): boolean {
  for (const key of Object.keys(params)) {
    if (String(run.params[key]) !== params[key]) { return false; }
  }
  return true;
}

export default function Dashboard() {
  const [runs, setRuns] = useState<RunLog[]>([]);
  const [shortlist, setShortlist] = useState<ShortlistEntry[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [algorithm, setAlgorithm] = useState<string>("");
  const [params, setParams] = useState<Record<string, string>>({});
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [shortlistOnly, setShortlistOnly] = useState<boolean>(false);

  const showRun = useCallback((run: RunLog) => {
    setAlgorithm(run.algorithm);
    setParams(paramsOf(run));
    setSelectedRunId(run.runId);
  }, []);

  useEffect(() => {
    fetchRuns()
      .then((data) => {
        setRuns(data.runs);
        setShortlist(data.shortlist);
        const best = bestRun(data.runs);
        const start = best ?? (data.runs.length > 0 ? data.runs[0] : null);
        if (start !== null) { showRun(start); }
        setLoading(false);
      })
      .catch((problem: unknown) => {
        setError(problem instanceof Error ? problem.message : String(problem));
        setLoading(false);
      });
  }, [showRun]);

  const shortlistIds = useMemo(() => {
    const ids = new Set<string>();
    for (const entry of shortlist) {
      ids.add(entry.runId);
    }
    return ids;
  }, [shortlist]);

  const algorithms = useMemo(() => {
    const present: string[] = [];
    for (const run of runs) {
      if (!present.includes(run.algorithm)) { present.push(run.algorithm); }
    }
    present.sort((a, b) => {
      const ia = ALGORITHM_ORDER.indexOf(a);
      const ib = ALGORITHM_ORDER.indexOf(b);
      return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib);
    });
    return present;
  }, [runs]);

  const algorithmRuns = useMemo(() => runs.filter((run) => run.algorithm === algorithm), [runs, algorithm]);
  const configRuns = useMemo(() => algorithmRuns.filter((run) => matchesParams(run, params)), [algorithmRuns, params]);
  const bestConfigRun = useMemo(() => bestRun(configRuns), [configRuns]);
  const bestConfigRunId = bestConfigRun !== null ? bestConfigRun.runId : null;

  const selectedRun = useMemo(() => {
    for (const run of runs) {
      if (run.runId === selectedRunId) { return run;}
    }
    return bestConfigRun;
  }, [runs, selectedRunId, bestConfigRun]);

  function selectAlgorithm(next: string) {
    const best = bestRun(runs.filter((run) => run.algorithm === next));
    if (best !== null) { showRun(best);}
    else {
      setAlgorithm(next);
      setParams({});
      setSelectedRunId(null);
    }
  }

  function changeParam(key: string, value: string) {
    const nextParams: Record<string, string> = { ...params, [key]: value };
    setParams(nextParams);
    const best = bestRun(algorithmRuns.filter((run) => matchesParams(run, nextParams)));
    setSelectedRunId(best !== null ? best.runId : null);
  }

  function toggleShortlist(runId: string) {
    let next: ShortlistEntry[];
    if (shortlistIds.has(runId)) { next = shortlist.filter((entry) => entry.runId !== runId);}
    else {
      next = [...shortlist, { runId, note: "" }];
    }
    setShortlist(next);
    void saveShortlist(next);
  }

  return (
    <div className="app">
      <header className="app-header">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/logo.svg" alt="Logo" className="logo-img" />
        <h1>Optimization Dashboard</h1>
      </header>

      {loading ? <div className="empty">Loading runs…</div> : null}

      {error !== null ? (
        <div className="empty error">
          <p><b>Could not load runs.</b></p>
          <p className="hint">{error}</p>
          <p className="hint">By default the app reads <span className="mono">../runs</span> (sibling of this frontend folder). Set <span className="mono">RUNS_DIR</span> to point elsewhere.</p>
        </div>
      ) : null}

      {!loading && error === null && runs.length === 0 ? (
        <div className="empty">No run files found in the runs folder.</div>
      ) : null}

      {!loading && error === null && runs.length > 0 ? (
        <div className="page">
          <div className="controls-row">
            <div className="control-block">
              <div className="section-label">Algorithm</div>
              <AlgorithmTabs algorithms={algorithms} selected={algorithm} onSelect={selectAlgorithm} />
            </div>
            <div className="control-block">
              <div className="section-label">Parameters</div>
              <ParameterBar algorithmRuns={algorithmRuns} selected={params} onChange={changeParam} />
            </div>
          </div>

          <RunPicker
            configRuns={configRuns}
            allRuns={runs}
            selectedRunId={selectedRunId}
            bestRunId={bestConfigRunId}
            shortlistIds={shortlistIds}
            shortlistOnly={shortlistOnly}
            onSelect={(runId) => { const run = runs.find((item) => item.runId === runId); if (run !== undefined) { showRun(run); } }}
            onToggleShortlist={toggleShortlist}
            onToggleShortlistOnly={setShortlistOnly}
          />

          {selectedRun !== null ? <KpiCards run={selectedRun} /> : null}

          {selectedRun !== null ? (
            <section className="panel">
              <div className="panel-title">Gantt chart</div>
              <GanttChart run={selectedRun} />
            </section>
          ) : null}

          <StatsBar runs={configRuns} />

          <section className="panel">
            <div className="panel-title">Comparison — {algorithmLabel(algorithm)}</div>
            <ComparisonTable algorithmRuns={algorithmRuns} />
          </section>

          <div className="analysis-divider">Analysis</div>

          {selectedRun !== null ? (
            <section className="panel">
              <div className="panel-title">Utilization &amp; bottlenecks</div>
              <UtilizationHeatmap run={selectedRun} />
            </section>
          ) : null}

          {selectedRun !== null && selectedRun.iterations !== null ? (
            <section className="panel">
              <div className="panel-title">Convergence (Tabu Search)</div>
              <ConvergenceChart run={selectedRun} />
            </section>
          ) : null}

          <section className="panel">
            <div className="panel-title">Quality vs. runtime (all runs)</div>
            <ParetoScatter runs={runs} />
          </section>
        </div>
      ) : null}
    </div>
  );
}
