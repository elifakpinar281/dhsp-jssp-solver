package at.fhv.solver;

public record Node(
        State state,
        Node parent,
        double heuristicValue
) {}
