package com.openbake.gateway.auth.jwt;

import java.util.Objects;

public class JwtVerificationException extends RuntimeException {

    private final JwtVerificationError error;

    public JwtVerificationException(JwtVerificationError error) {
        super(Objects.requireNonNull(error).name());
        this.error = error;
    }

    public JwtVerificationError error() {
        return error;
    }

    public String safeCode() {
        return error.safeCode();
    }
}
