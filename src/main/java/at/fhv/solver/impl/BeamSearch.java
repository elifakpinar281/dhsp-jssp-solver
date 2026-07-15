package at.fhv.solver.impl;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.*;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.Node;
import at.fhv.solver.State;
import at.fhv.visualization.SearchStatistics;

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
    public Schedule solve(JsspProblem jsspProblem) {
        State initialState = createInitialState(jsspProblem);
        double heuristicValue = heuristic.evaluate(initialState);
        Node initialNode = new Node(initialState, null, heuristicValue, null);

        List<Node> beam = new ArrayList<>();
        beam.add(initialNode);

        Set<State> visited = new HashSet<>();
        visited.add(initialState);

        statistics.setTotalOperations(countTotalOperations(jsspProblem));
        long expanded = 0;
        int maxDepth = 0;

        while (!beam.isEmpty()) {
            List<Node> candidates = new ArrayList<>();

            for (Node node : beam) {
                int depth = scheduledCount(node.state());
                if (depth > maxDepth) {maxDepth = depth;}

                if (jsspProblem.isGoal(node.state())) {
                    statistics.record(expanded, visited.size(), beam.size(), maxDepth);
                    return new Schedule(buildSchedule(node));
                }
                expanded++;

                for (Node child : expand(node, jsspProblem)) {
                    State childState = child.state();
                    if (!visited.contains(childState)) {
                        visited.add(childState);
                        candidates.add(child);
                    }
                }
            }
            candidates.sort(Comparator.comparingDouble(Node::heuristicValue));
            beam.clear();

            for (int i = 0; i < Math.min(beamWidth, candidates.size()); i++) {
                beam.add(candidates.get(i));
            }

            statistics.record(expanded, visited.size(), beam.size(), maxDepth);
            if (statistics.limitReached(expanded)) {
                return null;
            }
        }
        return null;
    }

    private State createInitialState(JsspProblem jsspProblem) {
        int[] nextOperation = new int[jsspProblem.getJobs().size()];
        int[] bathAvailableTime = new int[jsspProblem.totalBaths()];
        int[] jobAvailableTime = new int[jsspProblem.getJobs().size()];
        return new State(nextOperation, bathAvailableTime, jobAvailableTime);
    }

    private List<Node> expand(Node node, JsspProblem jsspProblem) {
        List<Node> children = new ArrayList<>();
        for (Operation operation : jsspProblem.getAvailableOperations(node.state())) {
            Transition transition = jsspProblem.applyOperation(node.state(), operation);
            if (transition == null ) {continue;}
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

    private int countTotalOperations(JsspProblem jsspProblem) {
        int total = 0;
        for (Job job : jsspProblem.getJobs()) {
            total = total + job.operations().size();
        }
        return total;
    }
}