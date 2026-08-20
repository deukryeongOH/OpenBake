package com.openbake.member.domain;

public interface AccessTokenRepository {
    void save(Long memberId, String accessToken);
    void blacklistByMemberId(Long memberId);
    boolean isBlacklisted(String accessToken);
}
