package com.clearledger.ledger.api;

import com.clearledger.ledger.account.InsufficientFundsException;
import com.clearledger.ledger.payment.IdempotencyConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class,
        MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> malformedRequest(Exception error, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", request);
        problem.setDetail("Check the request headers and JSON body.");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem(HttpStatus.FORBIDDEN, "Access denied", request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException error, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed", request);
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        problem.setProperty("fields", fields);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> invalidRequest(RuntimeException error, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", request);
        problem.setDetail(error.getMessage());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ProblemDetail> notFound(NoSuchElementException error, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Resource not found", request);
        problem.setDetail(error.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ResponseEntity<ProblemDetail> insufficientFunds(InsufficientFundsException error, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds", request);
        problem.setProperty("accountId", error.getAccountId());
        problem.setProperty("availableMinor", error.getAvailable());
        problem.setProperty("requestedMinor", error.getRequested());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ProblemDetail> idempotencyConflict(IdempotencyConflictException error, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Idempotency conflict", request);
        problem.setDetail(error.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> duplicatePayment(DataIntegrityViolationException error, HttpServletRequest request) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && ("uq_payment_idempotency".equals(violation.getConstraintName())
                        || "uq_payment_reference".equals(violation.getConstraintName()))) {
                ProblemDetail problem = problem(HttpStatus.CONFLICT, "Payment conflict", request);
                problem.setDetail("A payment with that idempotency key or merchant reference already exists.");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            }
        }
        return unexpected(error, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception error, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
        return ResponseEntity.internalServerError().body(problem);
    }

    private ProblemDetail problem(HttpStatus status, String title, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setType(URI.create("https://clearledger.dev/problems/" + status.value()));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
