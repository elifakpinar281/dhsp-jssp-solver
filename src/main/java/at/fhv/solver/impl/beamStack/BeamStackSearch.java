package at.fhv.solver.impl.beamStack;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.SchedulingProblem;
import at.fhv.solver.State;
import at.fhv.model.jssp.*;
import at.fhv.stats.SearchStatistics;

import java.util.*;

// Pseudocode von https://cdn.aaai.org/ICAPS/2005/ICAPS05-010.pdf
// Kann zurückgehen bei pruned Werten und diese erneut durchsuchen
// Findet eine Schedule und untersucht dann Alternativen
public class BeamStackSearch implements ISearchAlgorithm {
    private static final int INF = Integer.MAX_VALUE;
    private static final int MAX_SWEEPS = 100_000; // maximale Anzahl von Durchläufen
    private final IHeuristic heuristic;
    private final int beamWidth;
    private final SearchStatistics searchStatistics;
    private final int maxSweeps;
    private SchedulingProblem problem;
    private long expanded;
    private int maxDepthReached;

    public BeamStackSearch(IHeuristic heuristic, int beamWidth, SearchStatistics searchStatistics) {
        this(heuristic, beamWidth, searchStatistics, MAX_SWEEPS);
    }

    public BeamStackSearch(IHeuristic heuristic, int beamWidth, SearchStatistics searchStatistics, int maxSweeps) {
        this.heuristic = heuristic;
        this.beamWidth = beamWidth;
        this.searchStatistics = searchStatistics;
        this.maxSweeps = maxSweeps;
    }

    @Override
    public Schedule solve(SchedulingProblem problem) {
        this.problem = problem;
        this.expanded = 0;
        this.maxDepthReached = 0;
        searchStatistics.setTotalOperations(problem.totalOperations());

        State initialState = problem.createInitialState();
        BeamStackNode root = new BeamStackNode(initialState, null, null, lowerBound(initialState), 0);
        List<Range> items = new ArrayList<>();
        items.add(new Range(lowestKey(), boundKey(INF)));
        int upperBound = INF;
        BeamStackNode bestGoal = null;

        for (int sweep = 0; sweep < maxSweeps; sweep++) {
            SweepResult result = search(root, items, upperBound);

            if (result.goal() != null && result.makespan() < upperBound) {
                upperBound = result.makespan();
                bestGoal = result.goal();
                searchStatistics.reportNewBest(upperBound, expanded);
                if (!backtrack(items, upperBound)) { break; }
            } else {
                if (!backtrack(items, upperBound)) { break; }
            }
            searchStatistics.record(expanded, 0, 0, maxDepthReached);
            if (searchStatistics.limitReached(expanded)) {
                searchStatistics.markStoppedByLimit();
                break;
            }
        }
        if (bestGoal == null) { return null; }
        return new Schedule(buildSchedule(bestGoal));
    }

    private boolean backtrack(List<Range> items, int upperBound) {
        while (!items.isEmpty() && items.get(items.size() - 1).max.f() >= upperBound) {
            items.remove(items.size() - 1);
        }
        if (items.isEmpty()) { return false; }

        Range top = items.get(items.size() - 1);
        top.min = top.max;
        top.max = boundKey(upperBound);
        return true;
    }

    private SweepResult search(BeamStackNode root, List<Range> items, int upperBound) {
        Map<Integer, List<BeamStackNode>> open = new HashMap<>();
        open.put(0, new ArrayList<>(List.of(root)));
        long generated = 1;
        BeamStackNode bestGoal = null;
        int bestMakespan = upperBound;

        int layer = 0;
        while (open.containsKey(layer) && !open.get(layer).isEmpty()) {
            if (items.size() <= layer) {
                items.add(new Range(lowestKey(), boundKey(upperBound)));
            }
            Range range = items.get(layer);
            List<BeamStackNode> nextLayer = open.computeIfAbsent(layer + 1, key -> new ArrayList<>());
            Set<State> seenNextLayer = new HashSet<>();

            for (BeamStackNode node : open.get(layer)) {
                if (problem.isGoal(node.state())) {
                    int makespan = makespanOf(node.state());
                    if (makespan < bestMakespan) {
                        bestMakespan = makespan;
                        bestGoal = node;
                    }
                    continue;
                }
                expanded++;
                for (BeamStackNode child : expand(node)) {
                    int f = child.f();
                    Range.Key key = keyOf(child);
                    if (compareKeys(key, range.min) < 0 || compareKeys(key, range.max) >= 0) { continue; }
                    if (f >= upperBound) {continue;}
                    if (!seenNextLayer.add(child.state())) { continue; }
                    generated++;
                    nextLayer.add(child);
                }
            }
            if (nextLayer.size() > beamWidth) {
                pruneLayer(nextLayer, range);
            }

            if (layer + 1 > maxDepthReached) { maxDepthReached = layer + 1; }
            searchStatistics.record(expanded, (int) Math.min(generated, Integer.MAX_VALUE), nextLayer.size(), maxDepthReached);
            if (searchStatistics.limitReached(expanded)) {
                searchStatistics.markStoppedByLimit();
                break;
            }
            open.remove(layer);
            layer++;
        }
        return new SweepResult(bestGoal, bestMakespan);
    }

    private void pruneLayer(List<BeamStackNode> layerNodes, Range range) {
        layerNodes.sort((a, b) -> compareKeys(keyOf(a), keyOf(b)));

        Range.Key firstPruned = keyOf(layerNodes.get(beamWidth));
        if (compareKeys(firstPruned, range.max) < 0) {
            range.max = firstPruned;
        }

        while (layerNodes.size() > beamWidth) {
            layerNodes.remove(layerNodes.size() - 1);
        }
    }

    private static Range.Key keyOf(BeamStackNode node) {
        return new Range.Key(node.f(), node.state());
    }
    private static Range.Key lowestKey() {
        return new Range.Key(Integer.MIN_VALUE, null);
    }
    private static Range.Key boundKey(int bound) {
        return new Range.Key(bound, null);
    }

    private static int compareKeys(Range.Key a, Range.Key b) {
        int byF = Integer.compare(a.f(), b.f());
        if (byF != 0) { return byF; }
        if (a.state() == null && b.state() == null) { return 0; }
        if (a.state() == null) { return -1; }
        if (b.state() == null) { return 1; }
        return compareStates(a.state(), b.state());
    }

    private List<BeamStackNode> expand(BeamStackNode node) {
        List<BeamStackNode> children = new ArrayList<>();
        for (Transition transition : problem.expand(node.state())) {
            State childState = transition.state();
            int f = lowerBound(childState);
            children.add(new BeamStackNode(childState, node, transition.scheduledOperations(), f, node.dDepth() + 1));
        }
        return children;
    }

    private int lowerBound(State state) {
        return (int) heuristic.evaluate(state);
    }

    private int makespanOf(State state) {
        int makespan = 0;
        for (int available : state.jobAvailableTime()) {
            makespan = Math.max(makespan, available);
        }
        return makespan;
    }

    private static int compareStates(State a, State b) {
        int c = Arrays.compare(a.nextOperation(), b.nextOperation());
        if (c != 0) { return c; }
        c = Arrays.compare(a.jobAvailableTime(), b.jobAvailableTime());
        if (c != 0) { return c; }
        c = Arrays.compare(a.bathAvailableTime(), b.bathAvailableTime());
        if (c != 0) { return c; }
        return Arrays.compare(a.jobBath(), b.jobBath());
    }

    private List<ScheduledOperation> buildSchedule(BeamStackNode goal) {
        List<ScheduledOperation> schedule = new ArrayList<>();
        BeamStackNode current = goal;
        while (current != null && current.appliedOperations() != null) {
            List<ScheduledOperation> applied = current.appliedOperations();
            for (int i = applied.size() - 1; i >= 0; i--) {
                schedule.add(applied.get(i));
            }
            current = current.parent();
        }
        Collections.reverse(schedule);
        return schedule;
    }
}