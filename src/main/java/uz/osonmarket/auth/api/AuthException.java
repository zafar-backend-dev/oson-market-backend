package uz.osonmarket.auth.api;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Long retryAfterSeconds;

    public AuthException(HttpStatus status, String code) {
        this(status, code, null);
    }

    private AuthException(HttpStatus status, String code, Long retryAfterSeconds) {
        super(code);
        this.retryAfterSeconds = retryAfterSeconds;
        this.status = status;
        this.code = code;
    }

    public static AuthException cooldown(long seconds) {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, "auth.cooldown", seconds);
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
