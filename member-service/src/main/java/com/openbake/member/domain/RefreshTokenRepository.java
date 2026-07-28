package com.openbake.member.domain;

import java.util.Optional;

public interface RefreshTokenRepository {
    void save(Long memberId, String refreshToken);
    Optional<String> findByMemberId(Long memberId);
    void deleteByMemberId(Long memberId);
}
