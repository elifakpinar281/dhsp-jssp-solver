package at.fhv.solver.impl.beamStack;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.State;
import at.fhv.model.jssp.*;
import at.fhv.visualization.SearchStatistics;

import java.util.*;

public class BeamStackSearch implements ISearchAlgorithm {
    private static final int INF = Integer.MAX_VALUE;
    private static final int MAX_SWEEPS = 100_000;
    private final IHeuristic heuristic;
    private final int beamWidth;
    private final SearchStatistics searchStatistics;
    private final int maxSweeps;
    private JsspProblem jsspProblem;
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
    public Schedule solve(JsspProblem jsspProblem) {
        this.jsspProblem = jsspProblem;
        this.expanded = 0;
        this.maxDepthReached = 0;
        searchStatistics.setTotalOperations(countTotalOperations(jsspProblem));

        State initialState = createInitialState(jsspProblem);
        BeamStackNode root = new BeamStackNode(initialState, null, null, lowerBound(initialState), 0);
        List<Range> items = new ArrayList<>();
        items.add(new Range(0, INF));

        int upperBound = INF;
        BeamStackNode bestGoal = null;

        for (int sweep = 0; sweep < maxSweeps; sweep++) {
            SweepResult result = search(root, items, upperBound);

            if (result.goal() != null && result.makespan() < upperBound) {
                upperBound = result.makespan();
                bestGoal = result.goal();

                while (!items.isEmpty() && items.get(items.size()-1).fmax >= upperBound) {
                    items.remove(items.size()-1);
                }

                if (items.isEmpty()) { break; }
                Range top = items.get(items.size()-1);
                top.fmin = top.fmax;
                top.fmax = upperBound;
            } else {
                while (!items.isEmpty() && items.get(items.size()-1).fmin <= upperBound) {
                    items.remove(items.size()-1);
                }
                if (items.isEmpty()) { break; }
                Range top = items.get(items.size()-1);
                if (top.fmin >= top.fmax) {
                    items.remove(items.size()-1);
                    if (items.isEmpty()) { break; }
                    continue;
                }
                top.fmin = top.fmax;
                top.fmax = upperBound;
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



    private SweepResult search(BeamStackNode root, List<Range> items, int upperBound) {
        Map<Integer, List<BeamStackNode>> open = new HashMap<>();
        open.put(0, new ArrayList<>(List.of(root)));
        Map<State, BeamStackNode> seen = new HashMap<>();
        seen.put(root.state(), root);

        BeamStackNode bestGoal = null;
        int bestMakespan = upperBound;

        int layer = 0;
        while (open.containsKey(layer) && !open.get(layer).isEmpty()) {
            if (items.size() <= layer) {
                items.add(new Range(0, upperBound == INF ? INF : upperBound));
            }
            Range range = items.get(layer);
            List<BeamStackNode> nextLayer = open.computeIfAbsent(layer + 1, key -> new ArrayList<>());

            for (BeamStackNode node : open.get(layer)) {
                if (jsspProblem.isGoal(node.state())) {
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
                    if (f < range.fmin || f >= range.fmax) { continue; }
                    if (f >= upperBound) {continue;}
                    BeamStackNode previous = seen.get(child.state());
                    if (previous != null) { continue; }
                    seen.put(child.state(), child);
                    nextLayer.add(child);
                }
            }
            if (nextLayer.size() > beamWidth) {
                pruneLayer(nextLayer, range);
            }

            if (layer + 1 > maxDepthReached) { maxDepthReached = layer + 1; }
            searchStatistics.record(expanded, seen.size(), nextLayer.size(), maxDepthReached);
            if (searchStatistics.limitReached(expanded)) {
                searchStatistics.markStoppedByLimit();
                break;
            }
            layer++;
        }
        return new SweepResult(bestGoal, bestMakespan);
    }

    private void pruneLayer(List<BeamStackNode> layerNodes, Range range) {
        layerNodes.sort(Comparator.comparingInt((BeamStackNode n) -> n.f()).thenComparing(n -> n.state(), BeamStackSearch::compareStates));
        int smallestPruned = INF;
        for (int i = beamWidth; i < layerNodes.size(); i++) {
            smallestPruned = Math.min(smallestPruned, layerNodes.get(i).f());
        }
        range.fmax = Math.min(range.fmax, smallestPruned);

        while (layerNodes.size() > beamWidth) {
            layerNodes.remove(layerNodes.size() - 1);
        }
    }

    private List<BeamStackNode> expand(BeamStackNode node) {
        List<BeamStackNode> children = new ArrayList<>();
        for (Operation operation : jsspProblem.getAvailableOperations(node.state())) {
            Transition transition = jsspProblem.applyOperation(node.state(), operation);
            if (transition == null) {
                continue;
            }
            State childState = transition.state();
            int f = lowerBound(childState);
            children.add(new BeamStackNode(childState, node, transition.scheduledOperations(), f, node.dDepth() + 1));
        }
        return children;
    }


    private int lowerBound(State state) {
        int jobBound = 0;
        for (int jobId = 0; jobId < jsspProblem.getJobs().size(); jobId++) {
            List<Operation> operations = jsspProblem.getJobs().get(jobId).operations();
            int remaining = state.jobAvailableTime()[jobId];
            for (int i = state.nextOperation()[jobId]; i < operations.size(); i++) {
                remaining += operations.get(i).processingTime();
            }
            jobBound = Math.max(jobBound, remaining);
        }
        int machineBound = machineBound(state);
        return Math.max(jobBound, machineBound);
    }

    private int machineBound(State state) {
        int machineCount = jsspProblem.getMachines().size();
        int[] remainingLoad = new int[machineCount];

        for (int jobId = 0; jobId < jsspProblem.getJobs().size(); jobId++) {
            List<Operation> operations = jsspProblem.getJobs().get(jobId).operations();
            for (int i = state.nextOperation()[jobId]; i < operations.size(); i++) {
                Operation operation = operations.get(i);
                remainingLoad[operation.machineId()] += operation.processingTime();
            }
        }

        int machineBound = 0;
        for (int machineId = 0; machineId < machineCount; machineId++) {
            if (remainingLoad[machineId] == 0) { continue; }
            int capacity = Math.max(1, jsspProblem.capacityOf(machineId));
            int offset = jsspProblem.bathOffset(machineId);
            long freeSum = 0;
            for (int bath = offset; bath < offset + capacity; bath++) {
                int free = state.bathAvailableTime()[bath];
                if (free == State.BLOCKED) { free = 0; }
                freeSum += free;
            }
            int estimate = (int) ((freeSum + remainingLoad[machineId]) / capacity);
            machineBound = Math.max(machineBound, estimate);
        }
        return machineBound;
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

    private State createInitialState(JsspProblem jsspProblem) {
        int[] nextOperation = new int[jsspProblem.getJobs().size()];
        int[] bathAvailableTime = new int[jsspProblem.totalBaths()];
        int[] jobAvailableTime = new int[jsspProblem.getJobs().size()];
        int[] jobBath = new int[jsspProblem.getJobs().size()];
        Arrays.fill(jobBath, State.NO_BATH);
        return new State(nextOperation, bathAvailableTime, jobAvailableTime, jobBath);
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

    private int countTotalOperations(JsspProblem jsspProblem) {
        int total = 0;
        for (Job job : jsspProblem.getJobs()) {
            total = total + job.operations().size();
        }
        return total;
    }
}
