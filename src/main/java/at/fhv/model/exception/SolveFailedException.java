package at.fhv.model.exception;

public class SolveFailedException extends AppException {
    public SolveFailedException(String message) {
        super(message, ErrorCode.SOLVE_FAILED);
    }
}
