package at.fhv.solver.impl.beamStack;

public record SweepResult(
        BeamStackNode goal,
        int makespan
) {}
