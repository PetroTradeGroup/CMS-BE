package com.couponnumbergenerator.exception;

public class FuelTypeNotFoundException extends RuntimeException {

    public FuelTypeNotFoundException(Long id) {
        super("Fuel type not found with id: " + id);
    }

    public FuelTypeNotFoundException(String message) {
        super(message);
    }
}