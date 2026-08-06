package com.openbake.member.domain;

public interface TokenProvider {
    String createAccessToken(Long memberId, Role role);
    String createRefreshToken(Long memberId);
    Long getMemberId(String token);
    Role getRole(String token);
    boolean isValid(String token);
}
