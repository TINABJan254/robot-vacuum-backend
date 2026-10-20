package com.ht.robotvacuum.exception;

import com.ht.robotvacuum.dto.response.ResErrorDTO;
import com.ht.robotvacuum.dto.response.ResFieldErrorDTO;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> authentication(AuthenticationException exception, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return response(401, "Invalid credentials or refresh token", headers, List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> forbidden(AccessDeniedException exception, WebRequest request) {
        return response(403, "Access denied", new HttpHeaders(), List.of(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> notFound(ResourceNotFoundException exception, WebRequest request) {
        return response(404, "Resource not found", new HttpHeaders(), List.of(), request);
    }

    @ExceptionHandler({DuplicateResourceException.class, ResourceInUseException.class,
            DataIntegrityViolationException.class})
    public ResponseEntity<Object> conflict(Exception exception, WebRequest request) {
        return response(409, "Request conflicts with existing data", new HttpHeaders(), List.of(), request);
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<Object> invalidInput(Exception exception, WebRequest request) {
        return response(400, "Invalid request data", new HttpHeaders(), List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
        return response(500, "Internal server error", new HttpHeaders(), List.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ResFieldErrorDTO> fields = exception instanceof MethodArgumentNotValidException invalid
                ? invalid.getBindingResult().getFieldErrors().stream()
                    .map(error -> new ResFieldErrorDTO(error.getField(), "Invalid value")).toList()
                : List.of();
        String message = status.is5xxServerError() ? "Internal server error"
                : HttpStatus.valueOf(status.value()).getReasonPhrase();
        return response(status.value(), message, headers, fields, request);
    }

    private ResponseEntity<Object> response(int status, String message, HttpHeaders headers,
                                           List<ResFieldErrorDTO> fields, WebRequest request) {
        HttpHeaders outgoing = new HttpHeaders();
        outgoing.putAll(headers);
        outgoing.setCacheControl("no-store");
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        return new ResponseEntity<>(new ResErrorDTO(Instant.now(), status, message, path, fields),
                outgoing, HttpStatusCode.valueOf(status));
    }
}
