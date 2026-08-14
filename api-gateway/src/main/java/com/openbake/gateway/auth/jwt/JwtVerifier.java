package com.openbake.gateway.auth.jwt;

public interface JwtVerifier {
    JwtClaims verify(String rawToken);
}
