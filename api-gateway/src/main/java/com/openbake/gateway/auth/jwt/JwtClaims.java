package com.openbake.gateway.auth.jwt;

public record JwtClaims(
        long memberId,
        String role
) {}
