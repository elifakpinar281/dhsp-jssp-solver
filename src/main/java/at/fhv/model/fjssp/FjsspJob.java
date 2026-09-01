package at.fhv.model.fjssp;

import java.util.List;

public record FjsspJob(
        int jobId,
        List<FjsspOperation> operations
) {}
