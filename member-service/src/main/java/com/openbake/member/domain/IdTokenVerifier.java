package com.openbake.member.domain;

public interface IdTokenVerifier {
    OidcIdentity verify(AuthProvider provider, String idToken);
}
