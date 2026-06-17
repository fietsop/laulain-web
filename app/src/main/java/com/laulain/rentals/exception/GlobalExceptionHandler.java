package com.laulain.rentals.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ---- 404: Resource not found ----
    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleNotFound(ResourceNotFoundException ex,
                                  HttpServletRequest request,
                                  Model model) {
        log.warn("Resource not found: {}", ex.getMessage());

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorBody(404, ex.getMessage()));
        }

        model.addAttribute("errorCode", 404);
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/404";
    }

    // ---- 409: Duplicate resource ----
    @ExceptionHandler(DuplicateResourceException.class)
    public Object handleDuplicate(DuplicateResourceException ex,
                                   HttpServletRequest request) {
        log.warn("Duplicate resource: {}", ex.getMessage());

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(errorBody(409, ex.getMessage()));
        }

        ModelAndView mav = new ModelAndView("error/general");
        mav.addObject("errorMessage", ex.getMessage());
        return mav;
    }

    // ---- 400: Validation errors ----
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = errorBody(400, "Validation failed");
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // ---- 400: File upload errors ----
    @ExceptionHandler(FileUploadException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleFileUpload(FileUploadException ex) {
        return ResponseEntity.badRequest().body(errorBody(400, ex.getMessage()));
    }

    // ---- 413: File too large ----
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(errorBody(413, "File size exceeds the maximum allowed limit of 10MB"));
    }

    // ---- 500: Unexpected errors ----
    @ExceptionHandler(Exception.class)
    public Object handleGeneral(Exception ex, HttpServletRequest request, Model model) {
        log.error("Unexpected error processing request: {}", request.getRequestURI(), ex);

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(500, "An unexpected error occurred"));
        }

        model.addAttribute("errorCode", 500);
        model.addAttribute("errorMessage", "Something went wrong. Please try again.");
        return "error/500";
    }

    // ---- Helpers ----

    private boolean isApiRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        return (accept != null && accept.contains("application/json"))
                || uri.startsWith("/api/");
    }

    private Map<String, Object> errorBody(int status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
