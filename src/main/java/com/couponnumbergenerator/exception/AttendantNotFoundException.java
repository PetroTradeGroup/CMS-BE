package com.couponnumbergenerator.exception;

public class AttendantNotFoundException extends RuntimeException {

    public AttendantNotFoundException(Long id) {
        super("Attendant not found with id: " + id);
    }
}
