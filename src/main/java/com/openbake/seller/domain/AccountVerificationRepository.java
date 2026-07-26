package com.openbake.seller.domain;

import java.util.Optional;

public interface AccountVerificationRepository {
    void saveSession(String verificationRequestId, AccountVerificationSession session);
    Optional<AccountVerificationSession> findSession(String verificationRequestId);
    void deleteSession(String verificationRequestId);

    void saveVerifiedAccount(Long memberId, VerifiedAccount account);
    Optional<VerifiedAccount> findVerifiedAccountByMemberId(Long memberId);
}
