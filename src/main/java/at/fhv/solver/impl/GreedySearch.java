package at.fhv.solver.impl;

import at.fhv.model.jssp.*;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.evaluation.IHeuristic;
import at.fhv.solver.Node;
import at.fhv.solver.State;
import at.fhv.visualization.SearchStatistics;

import java.util.*;

public class GreedySearch implements ISearchAlgorithm {
    private final IHeuristic heuristic;
    private final SearchStatistics statistics;

    public GreedySearch(IHeuristic heuristic) {
        this(heuristic, new SearchStatistics());
    }

    public GreedySearch(IHeuristic heuristic, SearchStatistics statistics) {
        this.heuristic = heuristic;
        this.statistics = statistics;
    }

    @Override
    public Schedule solve(JsspProblem jsspProblem) {
        State initialState = createInitialState(jsspProblem);
        double heuristicValue = heuristic.evaluate(initialState);
        Node initialNode = new Node(initialState, null, heuristicValue, null);

        PriorityQueue<Node> frontier = new PriorityQueue<>((n1, n2)
                -> Double.compare(n1.heuristicValue(), n2.heuristicValue()));

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
        }
        return null;
    }

    private State createInitialState(JsspProblem problem) {
        int[] nextOperation = new int[problem.getJobs().size()];
        int[] machineAvailableTime = new int[problem.getMachines().size()];
        int[] jobAvailableTime = new int[problem.getJobs().size()];
        return new State(nextOperation, machineAvailableTime, jobAvailableTime);
    }

    private List<Node> expand(Node node, JsspProblem problem) {
        List<Node> children = new ArrayList<>();
        for (Operation operation : problem.getAvailableOperations(node.state())) {
            Transition transition = problem.applyOperation(node.state(), operation);
            if (transition == null) {continue;}
            double heuristicValue = heuristic.evaluate(transition.state());
            children.add(new Node(transition.state(), node, heuristicValue, transition.scheduledOperation()));
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
        while (current != null && current.appliedOperation() != null) {
            schedule.add(current.appliedOperation());
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