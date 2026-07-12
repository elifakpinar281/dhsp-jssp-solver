package at.fhv.solver.implementation;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.*;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.Node;
import at.fhv.solver.State;
import at.fhv.visualization.SearchStatistics;

import java.util.*;

public class HillClimbingSearch implements ISearchAlgorithm {
    private final IHeuristic heuristic;
    private final SearchStatistics statistics;

    public HillClimbingSearch(IHeuristic heuristic, SearchStatistics statistics) {
        this.heuristic = heuristic;
        this.statistics = statistics;
    }

    @Override
    public Schedule solve(JsspProblem jsspProblem) {
        State initialState = createInitialState(jsspProblem);
        double heuristicValue = heuristic.evaluate(initialState);
        Node initialNode = new Node(initialState, null, heuristicValue, null);

        Node currentNode = initialNode;

        Set<State> visited = new HashSet<>();
        visited.add(initialState);

        statistics.setTotalOperations(countTotalOperations(jsspProblem));
        long expanded = 0;
        int maxDepth = 0;

        while (true) {
            if (jsspProblem.isGoal(currentNode.state())) {
                statistics.record(expanded, visited.size(), 1, maxDepth);
                return new Schedule(buildSchedule(currentNode));
            }

            expanded++;
            Node bestchild = null;

            for (Node child : expand(currentNode, jsspProblem)) {
                if (visited.contains(child.state())) { continue; }
                visited.add(child.state());

                if (bestchild == null || child.heuristicValue() < bestchild.heuristicValue()){
                    bestchild = child;
                }
            }

            if (bestchild == null || bestchild.heuristicValue() >= currentNode.heuristicValue()) {
                statistics.record(expanded, visited.size(), 1, maxDepth);
                return null;
            }

            currentNode = bestchild;
            maxDepth = scheduledCount(currentNode.state());

        }
    }

    private State createInitialState(JsspProblem jsspProblem) {
        int[] nextOperation = new int[jsspProblem.getJobs().size()];
        int[] machineAvailableTime = new int[jsspProblem.getMachines().size()];
        int[] jobAvailableTime = new int[jsspProblem.getJobs().size()];
        return new State(nextOperation, machineAvailableTime, jobAvailableTime);
    }

    private List<Node> expand(Node node, JsspProblem jsspProblem) {
        List<Node> children = new ArrayList<>();
        for (Operation operation : jsspProblem.getAvailableOperations(node.state())) {
            Transition transition = jsspProblem.applyOperation(node.state(), operation);
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
