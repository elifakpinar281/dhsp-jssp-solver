package at.fhv.solver.impl;

import at.fhv.model.jssp.*;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.evaluation.IHeuristic;
import at.fhv.solver.Node;
import at.fhv.solver.State;
import at.fhv.visualization.SearchStatistics;

import java.util.*;

public class GreedySearch implements ISearchAlgorithm {
    private static final int DEFAULT_MAX_NODES = 1_500_000;

    private final IHeuristic heuristic;
    private final SearchStatistics statistics;
    private final int maxNodes;

    public GreedySearch(IHeuristic heuristic) {
        this(heuristic, new SearchStatistics(), DEFAULT_MAX_NODES);
    }

    public GreedySearch(IHeuristic heuristic, SearchStatistics statistics) {
        this(heuristic, statistics, DEFAULT_MAX_NODES);
    }

    public GreedySearch(IHeuristic heuristic, SearchStatistics statistics, int maxNodes) {
        this.heuristic = heuristic;
        this.statistics = statistics;
        this.maxNodes = maxNodes;
    }

    @Override
    public Schedule solve(JsspProblem jsspProblem) {
        State initialState = createInitialState(jsspProblem);
        double heuristicValue = heuristic.evaluate(initialState);
        Node initialNode = new Node(initialState, null, heuristicValue, null);

        PriorityQueue<Node> frontier = new PriorityQueue<>((n1, n2) -> {
            int byDepth = Integer.compare(scheduledCount(n2.state()), scheduledCount(n1.state()));
            if (byDepth != 0) {
                return byDepth;
            }
            return Double.compare(n1.heuristicValue(), n2.heuristicValue());
        });

        Set<State> reached = new HashSet<>();
        reached.add(initialState);
        frontier.add(initialNode);

        statistics.setTotalOperations(countTotalOperations(jsspProblem));
        long expanded = 0;
        int maxDepth = 0;

        while (!frontier.isEmpty()) {
            Node current = frontier.poll();

            if (jsspProblem.isGoal(current.state())) {
                statistics.record(expanded, reached.size(), frontier.size(), maxDepth);
                return new Schedule(buildSchedule(current));
            }
            expanded++;

            int depth = scheduledCount(current.state());
            if (depth > maxDepth) {
                maxDepth = depth;
            }

            for (Node child : expand(current, jsspProblem)) {
                State childState = child.state();
                if (!reached.contains(childState)) {
                    reached.add(childState);
                    frontier.add(child);
                }
            }
            statistics.record(expanded, reached.size(), frontier.size(), maxDepth);

            if (statistics.limitReached(expanded)) {
                return null;
            }
            if (reached.size() >= maxNodes) {
                statistics.markStoppedByLimit();
                return null;
            }
        }
        return null;
    }

    private State createInitialState(JsspProblem problem) {
        int[] nextOperation = new int[problem.getJobs().size()];
        int[] bathAvailableTime = new int[problem.totalBaths()];
        int[] jobAvailableTime = new int[problem.getJobs().size()];
        int[] jobBath = new int[problem.getJobs().size()];
        Arrays.fill(jobBath, State.NO_BATH);
        return new State(nextOperation, bathAvailableTime, jobAvailableTime, jobBath);
    }

    private List<Node> expand(Node node, JsspProblem problem) {
        List<Node> children = new ArrayList<>();
        for (Operation operation : problem.getAvailableOperations(node.state())) {
            Transition transition = problem.applyOperation(node.state(), operation);
            if (transition == null) {continue;}
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

    private int countTotalOperations(JsspProblem problem) {
        int total = 0;
        for (Job job : problem.getJobs()) {
            total = total + job.operations().size();
        }
        return total;
    }
}