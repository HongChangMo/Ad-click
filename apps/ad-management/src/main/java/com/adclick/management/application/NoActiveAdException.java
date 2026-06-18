package com.adclick.management.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NoActiveAdException extends RuntimeException {
    public NoActiveAdException() {
        super("No active ads available");
    }
}
