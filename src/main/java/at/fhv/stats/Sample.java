package at.fhv.stats;

public record Sample(
        long expansions,
        int reached,
        int frontier,
        int maxDepth
) {}
