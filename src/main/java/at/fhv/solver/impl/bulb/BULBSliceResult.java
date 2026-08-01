package at.fhv.solver.impl.bulb;

import java.util.List;

public record BULBSliceResult(
        List<BULBNode> slice,
        double value,
        int index
) {}
