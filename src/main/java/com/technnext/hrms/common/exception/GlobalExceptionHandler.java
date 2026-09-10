package com.technnext.hrms.common.exception;

import com.technnext.hrms.common.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Catches exceptions thrown anywhere in the app and turns them into a clean
 * ApiResponse with the right HTTP status.
 *
 * FIX: Added handlers for MaxUploadSizeExceededException (413),
 *      HttpMessageNotReadableException (400 malformed JSON),
 *      MethodArgumentTypeMismatchException (400 bad path/param types),
 *      MissingServletRequestParameterException (400 missing required params),
 *      and a proper catch-all that logs the error without exposing stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // The onboarding token is missing/expired/already-used/cancelled. 410 Gone
    // fits "this link no longer works" better than 400/404. `reason` in the
    // data field lets EmployeeOnboardingPage.tsx show the exact right state
    // (Invalid / Expired / Already Used) without parsing the message text.
    @ExceptionHandler(InvalidInviteException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleInvalidInvite(InvalidInviteException ex) {
        Map<String, String> data = new HashMap<>();
        data.put("reason", ex.getReason().name());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ApiResponse<>(false, ex.getMessage(), data, java.time.Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, "Validation failed", errors, java.time.Instant.now()));
    }

    // FIX: Catch malformed JSON bodies (e.g. syntax error in request payload) -> 400 not 500
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Malformed request body: " + ex.getMostSpecificCause().getMessage()));
    }

    // FIX: Catch bad path-variable / request-param types (e.g. letters where UUID expected) -> 400 not 500
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(msg));
    }

    // FIX: Catch missing required request parameters -> 400 not 500
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Missing required parameter: " + ex.getParameterName()));
    }

    // FIX: Catch file-too-large errors -> 413 not 500
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("Uploaded file exceeds the maximum allowed size (10 MB)."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed: " + ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to perform this action"));
    }

    // FIX: A hard delete (user/employee/etc.) failed because other rows still
    // reference it (attendance, leave requests, approvals, audit log, uploaded
    // documents, generated letters...). Previously this fell through to the
    // generic 500 "unexpected error" below, hiding the real, fixable reason.
    // Return 409 Conflict with an actionable message instead.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        System.err.println("[ERROR] Data integrity violation: " + ex.getMessage());
        String cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
        // BUGFIX: a unique-constraint violation on INSERT (e.g. two Invite
        // Employee submissions using the same employee code at almost the
        // same instant — the true race-condition case, since the app-level
        // existsByEmployeeCode() check can't see an insert that's still
        // mid-flight in another request) used to fall into the generic
        // "can't delete" message below, which is wrong and confusing for a
        // create action. Give it its own accurate message instead. Detection
        // is by column name in the DB driver's error detail, not a specific
        // constraint name, so it works regardless of how the constraint was
        // created (ddl-auto is "none" here — schema is managed manually).
        if (cause != null && cause.toLowerCase().contains("employee_code")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("That employee code was just taken by another invitation submitted " +
                            "at the same time. Please refresh the page and try again with the next available code."));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Can't permanently delete this record — it still has related history " +
                        "(attendance, leave, approvals, uploaded documents, or audit entries) referencing it. " +
                        "Deactivate it instead, or remove the related records first."));
    }

    // FIX: Catch-all — log the full stack trace server-side but return a safe generic
    //      message to the client (avoids leaking internal details in 500 responses).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        // Log properly in production; using System.err here to keep zero external deps.
        System.err.println("[ERROR] Unhandled exception: " + ex.getMessage());
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}