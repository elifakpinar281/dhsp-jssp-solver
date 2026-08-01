package at.fhv.solver.impl.bulb;

import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.solver.State;

import java.util.List;

public record BULBNode(
        State state,
        BULBNode parent,
        List<ScheduledOperation> appliedOperations,
        double heuristicValue,
        int dDepth
) {}
