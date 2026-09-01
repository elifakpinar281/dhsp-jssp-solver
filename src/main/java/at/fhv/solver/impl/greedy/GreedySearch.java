package at.fhv.solver.impl.greedy;

import at.fhv.model.jssp.*;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.SchedulingProblem;
import at.fhv.evaluation.IHeuristic;
import at.fhv.solver.Node;
import at.fhv.solver.State;
import at.fhv.stats.SearchStatistics;

import java.util.*;

public class GreedySearch implements ISearchAlgorithm {
    private static final int DEFAULT_MAX_NODES = 1_500_000;

    private final IHeuristic heuristic;
    private final SearchStatistics statistics;
    private final int maxNodes;

    public GreedySearch(IHeuristic heuristic) {
        this(heuristic, new SearchStatistics(), DEFAULT_MAX_NODES);
    }

    public GreedySearch(IHeuristic heuristic, SearchStatistics statistics, int maxNodes) {
        this.heuristic = heuristic;
        this.statistics = statistics;
        this.maxNodes = maxNodes;
    }

    @Override
    public Schedule solve(SchedulingProblem problem) {
        State initialState = problem.createInitialState();
        double heuristicValue = heuristic.evaluate(initialState);
        Node initialNode = new Node(initialState, null, heuristicValue, null);

        PriorityQueue<Node> frontier = new PriorityQueue<>((n1, n2) -> {
            int byHeuristic = Double.compare(n1.heuristicValue(), n2.heuristicValue());
            if (byHeuristic != 0) {
                return byHeuristic;
            }
            return Integer.compare(scheduledCount(n2.state()), scheduledCount(n1.state()));
        });

        Set<State> reached = new HashSet<>();
        reached.add(initialState);
        frontier.add(initialNode);

        statistics.setTotalOperations(problem.totalOperations());
        long expanded = 0;
        int maxDepth = 0;
        Node deepestNode = initialNode;

        while (!frontier.isEmpty()) {
            Node current = frontier.poll();

            if (problem.isGoal(current.state())) {
                statistics.record(expanded, reached.size(), frontier.size(), maxDepth);
                return new Schedule(buildSchedule(current));
            }
            expanded++;

            int depth = scheduledCount(current.state());
            if (depth > maxDepth) {
                maxDepth = depth;
            }
            if (depth > scheduledCount(deepestNode.state())) {
                deepestNode = current;
            }

            for (Node child : expand(current, problem)) {
                State childState = child.state();
                if (!reached.contains(childState)) {
                    reached.add(childState);
                    frontier.add(child);
                }
            }
            statistics.record(expanded, reached.size(), frontier.size(), maxDepth);

            if (statistics.limitReached(expanded) || reached.size() >= maxNodes) {
                statistics.markStoppedByLimit();
                return completeGreedily(deepestNode, problem);
            }
        }
        return null;
    }

    private Schedule completeGreedily(Node startNode, SchedulingProblem problem) {
        Node current = startNode;

        while (!problem.isGoal(current.state())) {
            List<Node> children = expand(current, problem);
            if (children.isEmpty()) { return null; }

            Node best = children.get(0);
            for (Node child : children) {
                if (child.heuristicValue() < best.heuristicValue()) {
                    best = child;
                }
            }
            current = best;
        }
        return new Schedule(buildSchedule(current));
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
