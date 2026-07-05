package at.fhv.model.jssp;

import java.util.List;

public record Schedule(
        List<ScheduledOperation> operations
) {}
