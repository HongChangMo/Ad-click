package com.adclick.management.interfaces.api;

import com.adclick.management.domain.InsufficientBalanceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Void> handleInsufficientBalance() {
        return ResponseEntity.notFound().build();
    }
}
