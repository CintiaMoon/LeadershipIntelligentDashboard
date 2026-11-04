package com.company.leadershipintelligence.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Bodies of the sign-in and account endpoints (decisions D65 to D68, D73). Their text forms never show a password or a token,
 * so a request that is logged by mistake does not leak one.
 */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record Register(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 1000) String password,
                           @NotBlank @Size(max = 200) String displayName) {
        @Override
        public String toString() {
            return "Register[redacted]";
        }
    }

    public record Login(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 1000) String password) {
        @Override
        public String toString() {
            return "Login[redacted]";
        }
    }

    public record EmailOnly(@NotBlank @Size(max = 254) String email) {
        @Override
        public String toString() {
            return "EmailOnly[redacted]";
        }
    }

    public record TokenOnly(@NotBlank @Size(max = 200) String token) {
        @Override
        public String toString() {
            return "TokenOnly[redacted]";
        }
    }

    public record ResetPassword(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 1000) String newPassword) {
        @Override
        public String toString() {
            return "ResetPassword[redacted]";
        }
    }

    public record ChangePassword(@NotBlank @Size(max = 1000) String currentPassword, @NotBlank @Size(max = 1000) String newPassword) {
        @Override
        public String toString() {
            return "ChangePassword[redacted]";
        }
    }
}
