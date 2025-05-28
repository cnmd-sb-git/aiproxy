package cn.mono.aiproxy.service.exceptions;

public class RelayException extends RuntimeException {
    public RelayException(String message) {
        super(message);
    }

    public RelayException(String message, Throwable cause) {
        super(message, cause);
    }
}
