package at.fhv.solver.implementation;

import at.fhv.evaluation.SearchRecorder;
import at.fhv.model.jssp.Job;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Operation;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.Node;
import at.fhv.solver.State;

import java.util.*;

public class GreedyBestFirstSearch implements ISearchAlgorithm {
    private final IHeuristic heuristic;
    private final SearchRecorder searchRecorder;

    public GreedyBestFirstSearch(IHeuristic heuristic) {
        this(heuristic, new SearchRecorder());
    }

    public GreedyBestFirstSearch(IHeuristic heuristic, SearchRecorder searchRecorder) {
        this.heuristic = heuristic;
        this.searchRecorder = searchRecorder;
    }

    @Override
    public Schedule solve(JsspProblem jsspProblem) {
        State initialState = createInitialState(jsspProblem);
        double heuristicValue = heuristic.evaluate(initialState);
        Node initialNode = new Node(initialState, null, heuristicValue);

        PriorityQueue<Node> frontier = new PriorityQueue<>((n1, n2)
                -> Double.compare(n1.heuristicValue(), n2.heuristicValue()));

        Map<State, Node> reached = new HashMap<>();
        reached.put(initialState, initialNode);
        frontier.add(initialNode);

        int totalOperations = countTotalOperations(jsspProblem);
        long expanded = 0;
        int maxDepth = 0;

        searchRecorder.start();
        try {
            while (!frontier.isEmpty()) {
                Node current = frontier.poll();

                if (jsspProblem.isGoal(current.state())) {
                    searchRecorder.record(expanded, reached.size(), frontier.size(), totalOperations, totalOperations);
                    return new Schedule(current.state().scheduledOperations());
                }

                expanded = expanded+1;

                int depth = current.state().scheduledOperations().size();
                if (depth > maxDepth) {
                    maxDepth = depth;
                }

                for (Node child : expand(current, jsspProblem)) {
                    State childState = child.state();
                    if (!reached.containsKey(childState)) {
                        reached.put(childState, child);
                        frontier.add(child);
                    }
                }
                searchRecorder.sample(expanded, reached.size(), frontier.size(), maxDepth, totalOperations);
            }
            return null;
        } finally {
            searchRecorder.finish();
        }
    }

    private State createInitialState(JsspProblem problem) {
        int[] nextOperation = new int[problem.getJobs().size()];
        int[] machineAvailableTime = new int[problem.getMachines().size()];
        int[] jobAvailableTime = new int[problem.getJobs().size()];
        return new State(nextOperation, machineAvailableTime, jobAvailableTime, new ArrayList<>());
    }


    private List<Node> expand (Node node, JsspProblem problem) {
        List<Node> children = new ArrayList<>();

        for (Operation operation : problem.getAvailableOperations(node.state())) {
            State newState = problem.applyOperation(node.state(), operation);
            double heuristicValue = heuristic.evaluate(newState);
            children.add(new Node(newState, node, heuristicValue));
        }
        return children;
    }

    private int countTotalOperations(JsspProblem problem) {
        int total = 0;
        for (Job job : problem.getJobs()) {
            total = total + job.operations().size();
        }
        return total;
    }
}
