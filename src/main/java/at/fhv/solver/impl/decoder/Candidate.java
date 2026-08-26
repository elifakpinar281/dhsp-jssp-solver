package at.fhv.solver.impl.decoder;

import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.Operation;
import at.fhv.model.jssp.Transition;

public record Candidate(
        Operation operation,
        Transition transition,
        int completion)
{}

