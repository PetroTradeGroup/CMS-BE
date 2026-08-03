package com.couponnumbergenerator.exception;

public class DepartmentNotFoundException extends RuntimeException {

    public DepartmentNotFoundException(Long id) {
        super("Department not found with id: " + id);
    }
}
