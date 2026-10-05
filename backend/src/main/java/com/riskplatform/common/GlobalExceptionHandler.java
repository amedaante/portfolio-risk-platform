package com.riskplatform.common;

import com.riskplatform.portfolio.PortfolioNotFoundException;
import com.riskplatform.risk.QuantEngineException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PortfolioNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(PortfolioNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(errorBody("Validation failed: " + ex.getMessage()));
    }

    @ExceptionHandler(QuantEngineException.class)
    public ResponseEntity<Map<String, Object>> handleQuantEngineFailure(QuantEngineException ex) {
        logger.warn("Quant engine request failed; downstream status: {}", ex.getDownstreamStatus());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(errorBody("Risk calculation service is temporarily unavailable"));
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("message", message);
        return body;
    }
}