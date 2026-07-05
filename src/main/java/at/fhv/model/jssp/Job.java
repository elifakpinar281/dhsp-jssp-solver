package at.fhv.model.jssp;

import java.util.List;

public record Job(
        int jobId,
        List<Operation> operations
) {}
