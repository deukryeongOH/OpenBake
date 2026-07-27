package com.openbake.member.domain;

import java.util.Optional;

public interface AuthCredentialRepository {
    AuthCredential save(AuthCredential authCredential);
    Optional<AuthCredential> findByProviderAndProviderId(AuthProvider provider, String providerId);
    boolean existsByProviderAndEmail(AuthProvider provider, String email);
    Optional<AuthCredential> findByProviderAndEmail(AuthProvider provider, String email);
    Optional<AuthCredential> findByMemberId(Long memberId);
}
