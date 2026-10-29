package in.trendsfit.web;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Customers only ever see a short, safe message. Technical details go to the server log. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Map<String, Object>> body(HttpStatus s, String code, String msg, Map<String, String> fields) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", msg);
        if (fields != null && !fields.isEmpty()) err.put("fields", fields);
        return ResponseEntity.status(s).body(Map.of("error", err));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) { return body(e.status(), e.code(), e.getMessage(), e.fields()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        Map<String, String> f = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe -> f.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION", "Please check the highlighted fields.", f);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> constraint(ConstraintViolationException e) { return body(HttpStatus.BAD_REQUEST, "VALIDATION", "Please check your input.", null); }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class, HttpMediaTypeNotSupportedException.class, MissingServletRequestPartException.class})
    ResponseEntity<Map<String, Object>> badInput(Exception e) { return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request could not be understood.", null); }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(Exception e) { return body(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found.", null); }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> method(Exception e) { return body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This action is not supported.", null); }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> tooBig(Exception e) { return body(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The file is too large (maximum 3 MB).", null); }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) { return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do this.", null); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "CONFLICT", "That value already exists or is still in use.", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> other(Exception e) {
        log.error("Unhandled error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR", "Something went wrong on our side. Please try again in a moment.", null);
    }
}
