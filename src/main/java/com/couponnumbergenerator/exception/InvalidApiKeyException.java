package com.couponnumbergenerator.exception;

/** A caller-supplied API key (e.g. on the inbound ERP sales webhook) is missing or wrong. */
public class InvalidApiKeyException extends RuntimeException {

    public InvalidApiKeyException(String message) {
        super(message);
    }
}
