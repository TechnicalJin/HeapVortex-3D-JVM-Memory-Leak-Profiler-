package com.heapvortex.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(JvmNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleJvmNotFound(JvmNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(HeapDumpNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleHeapDumpNotFound(HeapDumpNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(InvalidRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HprofFormatException.class)
    public ResponseEntity<Map<String, String>> handleHprofFormat(HprofFormatException ex) {
        log.warn("Invalid heap dump file: {}", ex.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(JvmConnectionException.class)
    public ResponseEntity<Map<String, String>> handleConnection(JvmConnectionException ex) {
        log.warn("JMX connection problem: {}", ex.getMessage(), ex);
        return error(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(HeapDumpException.class)
    public ResponseEntity<Map<String, String>> handleHeapDump(HeapDumpException ex) {
        log.warn("Heap dump problem: {}", ex.getMessage(), ex);
        return error(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}