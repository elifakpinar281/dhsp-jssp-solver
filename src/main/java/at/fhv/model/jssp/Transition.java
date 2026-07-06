package at.fhv.model.jssp;

import at.fhv.solver.State;

// result of applying 1 operation
public record Transition(
        State state,
        ScheduledOperation scheduledOperation
) {}
