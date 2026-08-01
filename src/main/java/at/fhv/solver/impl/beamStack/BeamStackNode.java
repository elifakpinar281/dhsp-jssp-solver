package at.fhv.solver.impl.beamStack;

import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.solver.State;

import java.util.List;

public record BeamStackNode(
        State state,
        BeamStackNode parent,
        List<ScheduledOperation> appliedOperations,
        int f,
        int dDepth
) {}
