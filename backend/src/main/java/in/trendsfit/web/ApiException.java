package in.trendsfit.web;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** A business error that is safe to show to the customer. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final transient Map<String, String> fields;

    public ApiException(HttpStatus status, String code, String message) { this(status, code, message, Map.of()); }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, String> fields() { return fields; }

    public static ApiException badRequest(String code, String msg) { return new ApiException(HttpStatus.BAD_REQUEST, code, msg); }
    public static ApiException unauthorized(String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", msg); }
    public static ApiException notFound(String msg) { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", msg); }
    public static ApiException forbidden(String msg) { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", msg); }
    public static ApiException conflict(String code, String msg) { return new ApiException(HttpStatus.CONFLICT, code, msg); }
    public static ApiException unavailable(String msg) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NOT_CONFIGURED", msg); }
}
