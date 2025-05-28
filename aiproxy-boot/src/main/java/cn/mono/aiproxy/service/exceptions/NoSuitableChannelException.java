package cn.mono.aiproxy.service.exceptions;

public class NoSuitableChannelException extends RuntimeException {
    public NoSuitableChannelException(String message) {
        super(message);
    }

    public NoSuitableChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
