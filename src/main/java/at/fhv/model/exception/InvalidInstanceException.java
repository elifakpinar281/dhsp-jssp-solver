package at.fhv.model.exception;

public class InvalidInstanceException extends AppException {
    public InvalidInstanceException(String message) {
        super(message, ErrorCode.INVALID_INSTANCE);
    }
}
