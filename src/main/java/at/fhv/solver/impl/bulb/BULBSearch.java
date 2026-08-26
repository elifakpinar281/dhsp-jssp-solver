package at.fhv.solver.impl.bulb;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.*;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.State;
import at.fhv.stats.SearchStatistics;

import java.util.*;

public class BULBSearch implements ISearchAlgorithm {
    private static final double GOAL_FOUND = 1.0;
    private static final double NO_PATH = Double.POSITIVE_INFINITY;
    private static final int DEFAULT_MAX_STORED = 2_000_000;
    private static final int DEFAULT_MAX_DISCREPANCIES = 50;
    private final IHeuristic heuristic;
    private final int beamWidth;
    private final SearchStatistics statistics;
    private final int maxStored;
    private final int maxDiscrepancies;
    private final Map<State, BULBNode> hashtable = new LinkedHashMap<>();
    private JsspProblem jsspProblem;
    private long expanded;
    private int maxDepthReached;
    private BULBNode foundGoal;

    public BULBSearch(IHeuristic heuristic, int beamWidth, SearchStatistics statistics) {
        this(heuristic, beamWidth, statistics, DEFAULT_MAX_STORED, DEFAULT_MAX_DISCREPANCIES);
    }

    public BULBSearch(IHeuristic heuristic, int beamWidth, SearchStatistics statistics, int maxStored, int maxDiscrepancies) {
        this.heuristic = heuristic;
        this.beamWidth = beamWidth;
        this.statistics = statistics;
        this.maxStored = maxStored;
        this.maxDiscrepancies = maxDiscrepancies;
    }

    @Override
    public Schedule solve(JsspProblem jsspProblem) {
        this.jsspProblem = jsspProblem;
        this.expanded = 0;
        this.maxDepthReached = 0;
        this.foundGoal = null;
        statistics.setTotalOperations(countTotalOperations(jsspProblem));

        State initialState = jsspProblem.createInitialState(jsspProblem);
        BULBNode root = new BULBNode(initialState, null, null, heuristic.evaluate(initialState), 0 );
        hashtable.clear();
        hashtable.put(initialState, root);

        for (int discrepancies = 0; discrepancies <= maxDiscrepancies; discrepancies++) {
            hashtable.clear();
            hashtable.put(initialState, root);
            foundGoal = null;
            double pathLength = BULBProbe(0, discrepancies);

            statistics.record(expanded, hashtable.size(), 0, maxDepthReached);
            if (pathLength < NO_PATH && foundGoal != null) {
                return new Schedule(buildSchedule(foundGoal));
            }
            if(statistics.limitReached(expanded)) {
                statistics.markStoppedByLimit();
                return null;
            }
        }
        return null;
    }

    private double BULBProbe (int depth, int discrepancies) {
        BULBSliceResult result = nextSlice(depth, 0);
        if (result.value() >= 0) return result.value();

        if (discrepancies == 0) {
            if (result.slice().isEmpty()) { return NO_PATH; }
            double pathLength = BULBProbe(depth + 1, 0);
            removeFomTable(result.slice());
            return pathLength;
        }

        if (!result.slice().isEmpty()) {
            removeFomTable(result.slice());
            int index = result.index();

            while (true) {
                BULBSliceResult alternative = nextSlice(depth, index);
                index = alternative.index();

                if (alternative.value() >= 0) {
                    if (alternative.value() < NO_PATH) {
                        return alternative.value();
                    }
                    break;
                }

                if (alternative.slice().isEmpty()) { continue; }
                double pathLength = BULBProbe(depth + 1, discrepancies - 1);
                removeFomTable(alternative.slice());
                if (pathLength < NO_PATH) { return pathLength; }
            }
        }

        BULBSliceResult best = nextSlice(depth, 0);
        if (best.value() >= 0) {return best.value(); }
        if (best.slice().isEmpty()) { return NO_PATH; }
        double pathLength =BULBProbe(depth + 1, discrepancies);
        removeFomTable(best.slice());
        return pathLength;
    }

    private BULBSliceResult nextSlice(int depth, int index) {
        List<BULBNode> currentLayer = layerAt(depth);
        List<BULBNode> successors = generateSucessors(currentLayer);
        if (successors.isEmpty() || index >= successors.size()) {
            return new BULBSliceResult(new ArrayList<>(), NO_PATH, -1);
        }
        BULBNode goal = bestGoal(successors);
        if (goal != null) {
            foundGoal = goal;
            return new BULBSliceResult(new ArrayList<>(), GOAL_FOUND, -1);
        }

        expanded++;
        if (depth + 1 > maxDepthReached) { maxDepthReached = depth + 1; }
        statistics.record(expanded, hashtable.size(), Math.min(beamWidth, successors.size()), maxDepthReached);

        List<BULBNode> slice = new ArrayList<>();
        int i = index;
        while (i < successors.size() && slice.size() < beamWidth) {
            BULBNode candidate = successors.get(i);
            if (!hashtable.containsKey(candidate.state())) {
                slice.add(candidate);
                hashtable.put(candidate.state(), candidate);
                if (hashtable.size()>= maxStored) {
                    removeFomTable(slice);
                    return new BULBSliceResult(new ArrayList<>(), NO_PATH, -1);
                }
            }
            i++;
        }
        return new BULBSliceResult(slice, -1.0, i);
    }

    private List<BULBNode> generateSucessors(List<BULBNode> currentLayer) {
        List<BULBNode> successors = new ArrayList<>();
        for (BULBNode node : currentLayer) {
            for (Operation operation : jsspProblem.getAvailableOperations(node.state())) {
                Transition transition = jsspProblem.applyOperation(node.state(), operation);
                if (transition == null) { continue; }
                State childState = transition.state();
                if (hashtable.containsKey(childState)) { continue; }
                double heur = heuristic.evaluate(childState);
                successors.add(new BULBNode(childState, node, transition.scheduledOperations(), heur, node.dDepth() + 1));
            }
        }
        successors.sort(Comparator.comparingDouble((BULBNode node) -> node.heuristicValue()).thenComparing(node -> node.state(), BULBSearch::compareStates));
        return successors;
    }

    private List<BULBNode> layerAt(int depth) {
        List<BULBNode> layer = new ArrayList<>();
        for (BULBNode node : hashtable.values()) {
            if (node.dDepth() == depth) { layer.add(node); }
        }
        return layer;
    }

    private BULBNode bestGoal(List<BULBNode> successors) {
        BULBNode best = null;
        for (BULBNode node : successors) {
            if (jsspProblem.isGoal(node.state())) {
                if (best == null || makespanOf(node.state()) < makespanOf(best.state())) { best = node; }
            }
        }
        return best;
    }

    private void removeFomTable(List<BULBNode> slice) {
        for (BULBNode node : slice) {
            hashtable.remove(node.state());
        }
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
        if (c !=0) return c;
        c = Arrays.compare(a.jobAvailableTime(), b.jobAvailableTime());
        if (c != 0) return c;
        c = Arrays.compare(a.bathAvailableTime(), b.bathAvailableTime());
        if (c != 0) return c;
        return Arrays.compare(a.jobBath(), b.jobBath());
    }

    private List<ScheduledOperation> buildSchedule(BULBNode goal) {
        List<ScheduledOperation> schedule = new ArrayList<>();
        BULBNode current = goal;

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