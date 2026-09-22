package at.fhv.solver.impl.beam;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.*;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.SchedulingProblem;
import at.fhv.solver.Node;
import at.fhv.solver.State;
import at.fhv.stats.SearchStatistics;

import java.util.*;

public class BeamSearch implements ISearchAlgorithm {
    private final IHeuristic heuristic;
    private final int beamWidth;
    private final SearchStatistics statistics;

    public BeamSearch(IHeuristic heuristic, int beamWidth, SearchStatistics statistics) {
        this.heuristic = heuristic;
        this.beamWidth = beamWidth;
        this.statistics = statistics;
    }

    @Override
    public Schedule solve(SchedulingProblem problem) {
        State initialState = problem.createInitialState();
        double heuristicValue = heuristic.evaluate(initialState);
        Node initialNode = new Node(initialState, null, heuristicValue, null);

        TreeMap<Integer, List<Node>> levels = new TreeMap<>();
        levels.computeIfAbsent(scheduledCount(initialState), key -> new ArrayList<>()).add(initialNode);
        Map<Integer, Set<State>> visitedPerLevel = new HashMap<>();
        long generated = 1;

        statistics.setTotalOperations(problem.totalOperations());
        long expanded = 0;
        int maxDepth = 0;

        Node bestGoal = null;

        while (!levels.isEmpty()) {
            Map.Entry<Integer, List<Node>> entry = levels.pollFirstEntry();
            int depth = entry.getKey();
            List<Node> level = entry.getValue();
            visitedPerLevel.remove(depth);

            if (depth > maxDepth) { maxDepth = depth; }

            level.sort(Comparator.comparingDouble(Node::heuristicValue));
            List<Node> beam = level.subList(0, Math.min(beamWidth, level.size()));

            for (Node node : beam) {
                if (problem.isGoal(node.state())) {
                    if (bestGoal == null || makespanOf(node) < makespanOf(bestGoal)) {
                        bestGoal = node;
                        statistics.reportNewBest(makespanOf(node), expanded);
                    }
                    continue;
                }
                expanded++;

                for (Node child : expand(node, problem)) {
                    State childState = child.state();
                    int childDepth = scheduledCount(childState);

                    Set<State> levelSet = visitedPerLevel.get(childDepth);
                    if (levelSet == null) {
                        levelSet = new HashSet<>();
                        visitedPerLevel.put(childDepth, levelSet);
                    }
                    if (!levelSet.add(childState)) { continue; }

                    generated++;
                    levels.computeIfAbsent(childDepth, key -> new ArrayList<>()).add(child);
                }
            }

            statistics.record(expanded, (int) Math.min(generated, Integer.MAX_VALUE), beam.size(), maxDepth);
            if (statistics.limitReached(expanded)) {
                statistics.markStoppedByLimit();
                return bestGoal == null ? null : new Schedule(buildSchedule(bestGoal));
            }
        }

        if (bestGoal == null) { return null; }
        return new Schedule(buildSchedule(bestGoal));
    }

    private int makespanOf(Node node) {
        int makespan = 0;
        for (int available : node.state().jobAvailableTime()) {
            makespan = Math.max(makespan, available);
        }
        return makespan;
    }

    private List<Node> expand(Node node, SchedulingProblem problem) {
        List<Node> children = new ArrayList<>();
        for (Transition transition : problem.expand(node.state())) {
            double heuristicValue = heuristic.evaluate(transition.state());
            children.add(new Node(transition.state(), node, heuristicValue, transition.scheduledOperations()));
        }
        return children;
    }

    private int scheduledCount(State state) {
        int count = 0;
        for (int next : state.nextOperation()) {
            count = count + next;
        }
        return count;
    }

    private List<ScheduledOperation> buildSchedule(Node goal) {
        List<ScheduledOperation> schedule = new ArrayList<>();
        Node current = goal;
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
