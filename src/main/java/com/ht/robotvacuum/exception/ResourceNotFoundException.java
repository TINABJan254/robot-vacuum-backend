package com.ht.robotvacuum.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, long id) {
        super(resource + " with id " + id + " was not found");
    }
}
