package com.company.leadershipintelligence.api.exception;

import com.company.leadershipintelligence.api.response.ApiError;
import com.company.leadershipintelligence.config.beans.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/** Writes the standard error body for failures that happen before a controller runs (no token, wrong role). */
@Component
public class ApiErrorWriter {

    private final ObjectMapper json;

    public ApiErrorWriter(ObjectMapper json) {
        this.json = json;
    }

    public void unauthenticated(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        write(response, 401, "UNAUTHENTICATED", "Authentication is required.");
    }

    public void accessDenied(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
        write(response, 403, "ACCESS_DENIED", "The caller's role does not allow this operation.");
    }

    /** A state-changing request from a session without a valid CSRF token (decision D68). */
    public void csrfRejected(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
        write(response, 403, "CSRF_TOKEN_INVALID", "The request was refused because its CSRF token is missing or not valid.");
    }

    /** A tenant endpoint was called by a session without naming the company (decision D69). */
    public void companyRequired(HttpServletResponse response) throws IOException {
        write(response, 400, "COMPANY_REQUIRED", "The X-Company-Id header must name the company.");
    }

    /** No ACTIVE membership in the named company. The same answer whether or not the company exists, so existence is not revealed. */
    public void companyDenied(HttpServletResponse response) throws IOException {
        write(response, 403, "COMPANY_ACCESS_DENIED", "You do not have access to this company.");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), new ApiError(code, message, Map.of(), MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
