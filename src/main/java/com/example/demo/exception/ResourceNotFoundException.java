package com.example.demo.exception;

/** A referenced entity does not exist. Mapped to HTTP 404 by {@link GlobalExceptionHandler}. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found");
    }
}
