package at.fhv.solver.impl.decoder;

import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.Operation;

public record Candidate(
        Job job,
        Operation operation,
        int remainingDwell,
        boolean atRisk
) {}
