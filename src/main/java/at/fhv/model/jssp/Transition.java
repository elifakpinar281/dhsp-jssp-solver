package at.fhv.model.jssp;

import at.fhv.solver.State;

import java.util.List;

public record Transition(
        State state,
        List<ScheduledOperation> scheduledOperations
) {}