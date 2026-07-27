package com.openbake.member.infrastructure.oauth;

public record OidcIdentity(String providerId, String email, String name) {}
