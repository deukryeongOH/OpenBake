package com.openbake.member.infrastructure;

import com.openbake.member.domain.AuthCredential;
import com.openbake.member.domain.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthCredentialJpaRepository extends JpaRepository<AuthCredential, Long> {
    Optional<AuthCredential> findByProviderAndProviderId(AuthProvider provider, String providerId);
    boolean existsByProviderAndEmail(AuthProvider provider, String email);
    Optional<AuthCredential> findByProviderAndEmail(AuthProvider provider, String email);
    Optional<AuthCredential> findByMemberId(Long memberId);
}
