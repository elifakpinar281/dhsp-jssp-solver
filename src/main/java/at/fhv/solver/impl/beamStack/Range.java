package at.fhv.solver.impl.beamStack;

import at.fhv.solver.State;

public class Range {
    public Key min;
    public Key max;

    public Range(Key min, Key max) {
        this.min = min;
        this.max = max;
    }

    public record Key(int f, State state) {}
}
