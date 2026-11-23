package uz.osonmarket.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class AuthErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(AuthErrorHandler.class);
    private final AuthMessages messages;

    public AuthErrorHandler(AuthMessages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ErrorBody> auth(AuthException ex, Locale locale) {
        Long seconds = ex.retryAfterSeconds();
        if (seconds == null) {
            return error(ex.status(), ex.code(), List.of(), locale);
        }
        return ResponseEntity.status(ex.status()).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_LANGUAGE, AuthMessages.language(locale))
                .header(HttpHeaders.RETRY_AFTER, seconds.toString())
                .body(new ErrorBody(ex.code(), messages.text(ex.code(), locale, seconds), List.of(), seconds));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex, Locale locale) {
        List<String> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField()).distinct().sorted().toList();
        return error(HttpStatus.BAD_REQUEST, "request.invalid", fields, locale);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> uploadSize(Locale locale) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "profile.avatar.size", List.of(), locale);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> malformed(Locale locale) {
        return error(HttpStatus.BAD_REQUEST, "request.invalid", List.of(), locale);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> conflict(DataIntegrityViolationException ex, jakarta.servlet.http.HttpServletRequest request, Locale locale) {
        String path = request.getServletPath();
        if (path.startsWith("/api/orders") || path.startsWith("/api/admin/orders")) {
            log.warn("Order database conflict", ex);
            return error(HttpStatus.CONFLICT, "order.data.conflict", List.of(), locale);
        }
        return error(HttpStatus.CONFLICT, "auth.account.exists", List.of(), locale);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception ex, Locale locale) {
        log.error("Auth request failed ({})", ex.getClass().getSimpleName());
        HttpStatus status = ex instanceof org.springframework.web.ErrorResponse webError
                ? HttpStatus.resolve(webError.getStatusCode().value()) : HttpStatus.INTERNAL_SERVER_ERROR;
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return error(status, status.is4xxClientError() ? "request.invalid" : "request.failed", List.of(), locale);
    }

    private ResponseEntity<ErrorBody> error(HttpStatus status, String code, List<String> fields, Locale locale) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header("Content-Language", AuthMessages.language(locale))
                .body(new ErrorBody(code, messages.text(code, locale), fields, null));
    }

    record ErrorBody(String code, String message, List<String> fields,
                     @JsonInclude(JsonInclude.Include.NON_NULL) Long retryAfterSeconds) {}
}
