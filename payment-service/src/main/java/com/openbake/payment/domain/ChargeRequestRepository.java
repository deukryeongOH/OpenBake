package com.openbake.payment.domain;

import java.util.List;
import java.util.Optional;

public interface ChargeRequestRepository {
    ChargeRequest save(ChargeRequest chargeRequest);
    Optional<ChargeRequest> findById(Long id);
    Optional<ChargeRequest> findByPgOrderIdForUpdate(String pgOrderId);
    Optional<ChargeRequest> findByPgPaymentKey(String pgPaymentKey);
    Optional<ChargeRequest> findByIdForUpdate(Long id);
    boolean existsByMemberIdAndStatusIn(Long memberId, List<ChargeStatus> statuses);
    List<ChargeRequest> findByMemberIdAndStatus(Long memberId, ChargeStatus status);
    List<ChargeRequest> findByStatus(ChargeStatus status);
}
