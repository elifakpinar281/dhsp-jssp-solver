package at.fhv.benchmark;

import java.util.List;

public record ValidationResult(
        boolean valid,
        List<String> violations
) {
    public String summary() {
        if (valid) { return "valid"; }
        else { return "invalid, violations: " + violations.size(); }
    }
}
