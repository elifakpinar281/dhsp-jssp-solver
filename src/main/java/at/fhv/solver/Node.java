package at.fhv.solver;

import at.fhv.model.jssp.ScheduledOperation;

import java.util.List;

public record Node(
        State state,
        Node parent,
        double heuristicValue,
        List<ScheduledOperation> appliedOperations
) {}