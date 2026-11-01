package com.company.leadershipintelligence.api.controller;

/** A request that is malformed in a way bean validation cannot express; answered with HTTP 400 INVALID_REQUEST. */
public class ApiRequestException extends RuntimeException {

    public ApiRequestException(String message) {
        super(message);
    }
}
