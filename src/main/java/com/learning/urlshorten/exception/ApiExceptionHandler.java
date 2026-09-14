package com.learning.urlshorten.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> databaseFailure(DataAccessException exception) {
        return ResponseEntity.status(503).body(Map.of("message", "Storage temporarily unavailable"));
    }
}
