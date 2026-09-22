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
    private final boolean completeOnLimit;

    private record QueueEntry(Node node, int depth) {}

    public GreedySearch(IHeuristic heuristic) {
        this(heuristic, new SearchStatistics(), DEFAULT_MAX_NODES);
    }

    public GreedySearch(IHeuristic heuristic, SearchStatistics statistics, int maxNodes) {
        this(heuristic, statistics, maxNodes, true);
    }

    public GreedySearch(IHeuristic heuristic, SearchStatistics statistics, int maxNodes, boolean completeOnLimit) {
        this.heuristic = heuristic;
        this.statistics = statistics;
        this.maxNodes = maxNodes;
        this.completeOnLimit = completeOnLimit;
    }

    @Override
    public Schedule solve(SchedulingProblem problem) {
        State initialState = problem.createInitialState();
        double heuristicValue = heuristic.evaluate(initialState);
        Node initialNode = new Node(initialState, null, heuristicValue, null);

        PriorityQueue<QueueEntry> frontier = new PriorityQueue<>((e1, e2) -> {
            int byHeuristic = Double.compare(e1.node().heuristicValue(), e2.node().heuristicValue());
            if (byHeuristic != 0) {
                return byHeuristic;
            }
            return Integer.compare(e2.depth(), e1.depth());
        });

        Set<State> reached = new HashSet<>();
        reached.add(initialState);
        frontier.add(new QueueEntry(initialNode, scheduledCount(initialState)));

        statistics.setTotalOperations(problem.totalOperations());
        long expanded = 0;
        int maxDepth = 0;
        Node deepestNode = initialNode;
        int deepestDepth = 0;

        while (!frontier.isEmpty()) {
            QueueEntry entry = frontier.poll();
            Node current = entry.node();

            if (problem.isGoal(current.state())) {
                statistics.record(expanded, reached.size(), frontier.size(), maxDepth);
                statistics.reportNewBest(makespanOf(current.state()), expanded);
                return new Schedule(buildSchedule(current));
            }
            expanded++;

            int depth = entry.depth();
            if (depth > maxDepth) {
                maxDepth = depth;
            }
            if (depth > deepestDepth) {
                deepestNode = current;
                deepestDepth = depth;
            }

            for (Node child : expand(current, problem)) {
                if (reached.add(child.state())) {
                    frontier.add(new QueueEntry(child, scheduledCount(child.state())));
                }
            }
            statistics.record(expanded, reached.size(), frontier.size(), maxDepth);

            if (statistics.limitReached(expanded) || reached.size() >= maxNodes) {
                statistics.markStoppedByLimit();
                if (!completeOnLimit) {
                    return null;
                }
                Schedule completed = completeGreedily(deepestNode, problem);
                if (completed != null) {
                    statistics.reportNewBest(makespanOf(completed), expanded);
                }
                return completed;
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

    private int makespanOf(State state) {
        int makespan = 0;
        for (int available : state.jobAvailableTime()) {
            makespan = Math.max(makespan, available);
        }
        return makespan;
    }

    private int makespanOf(Schedule schedule) {
        int makespan = 0;
        for (ScheduledOperation operation : schedule.operations()) {
            makespan = Math.max(makespan, operation.endTime());
        }
        return makespan;
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
