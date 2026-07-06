package at.fhv.solver;

import at.fhv.model.jssp.ScheduledOperation;

public record Node(
        State state,
        Node parent,
        double heuristicValue,
        ScheduledOperation appliedOperation
) {}
