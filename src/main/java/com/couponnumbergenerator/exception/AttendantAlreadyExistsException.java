package com.couponnumbergenerator.exception;

public class AttendantAlreadyExistsException extends RuntimeException {

    public AttendantAlreadyExistsException(String username) {
        super("An account with username '%s' already exists".formatted(username));
    }
}
