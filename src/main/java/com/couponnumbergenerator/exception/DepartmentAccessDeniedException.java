package com.couponnumbergenerator.exception;

/** The caller's JWT department claim doesn't match the approval request's relevant department field (AD-3). */
public class DepartmentAccessDeniedException extends RuntimeException {

    public DepartmentAccessDeniedException(String message) {
        super(message);
    }
}
