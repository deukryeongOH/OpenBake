package com.openbake.member.infrastructure;

import com.openbake.member.domain.AuthCredential;
import com.openbake.member.domain.AuthCredentialRepository;
import com.openbake.member.domain.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuthCredentialRepositoryImpl implements AuthCredentialRepository {

    private final AuthCredentialJpaRepository authCredentialJpaRepository;

    @Override
    public AuthCredential save(AuthCredential authCredential) {
        return authCredentialJpaRepository.save(authCredential);
    }

    @Override
    public Optional<AuthCredential> findByProviderAndProviderId(AuthProvider provider, String providerId) {
        return authCredentialJpaRepository.findByProviderAndProviderId(provider, providerId);
    }

    @Override
    public boolean existsByProviderAndEmail(AuthProvider provider, String email) {
        return authCredentialJpaRepository.existsByProviderAndEmail(provider, email);
    }

    @Override
    public Optional<AuthCredential> findByProviderAndEmail(AuthProvider provider, String email) {
        return authCredentialJpaRepository.findByProviderAndEmail(provider, email);
    }

    @Override
    public Optional<AuthCredential> findByMemberId(Long memberId) {
        return authCredentialJpaRepository.findByMemberId(memberId);
    }

}
