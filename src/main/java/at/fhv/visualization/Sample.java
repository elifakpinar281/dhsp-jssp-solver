package at.fhv.visualization;

public record Sample(
        long expansions,
        int reached,
        int frontier,
        int maxDepth
) {}
