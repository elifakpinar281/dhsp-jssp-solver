package at.fhv.solver.implementation;

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

    public GreedyBestFirstSearch(IHeuristic heuristic) {
        this.heuristic = heuristic;
    }

    @Override
    public Schedule solve(JsspProblem jsspProblem) {
        State initialState = createInitialState(jsspProblem);
        double heuristicValue = heuristic.evaluate(initialState);
        Node initialNode = new Node(initialState, null, heuristicValue);

        PriorityQueue<Node> frontier = new PriorityQueue<>((n1, n2)
                -> Double.compare(n1.heuristicValue(), n2.heuristicValue()));

        Map<State, Node> reached = new HashMap <>();
        reached.put(initialState, initialNode);
        frontier.add(initialNode);

        while (!frontier.isEmpty()) {
            Node current = frontier.poll();

            if (jsspProblem.isGoal(current.state())) {
                return new Schedule(current.state().scheduledOperations());
            }

            for (Node child : expand(current, jsspProblem)) {
                State childState = child.state();
                if (!reached.containsKey(childState)) {
                    reached.put(childState, child);
                    frontier.add(child);
                }
            }
        }
        return null;
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
}
