package com.openbake.payment.infrastructure;

import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.domain.ChargeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChargeRequestJpaRepository extends JpaRepository<ChargeRequest, Long> {

    /**
     * 비관적 락으로 ChargeRequest 조회.
     * markInProgress()에서 더블클릭/네트워크 재시도로 같은 pgOrderId 승인 요청이
     * 동시에 올 때, 한 쪽만 READY → IN_PROGRESS 전이를 하도록 보장한다.
     *
     * 락 순서: charge_requests → deposit_accounts.
     * completeCharge()도 이 순서를 따른다. 뒤집으면 데드락.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChargeRequest c WHERE c.pgOrderId = :pgOrderId")
    Optional<ChargeRequest> findByPgOrderIdForUpdate(@Param("pgOrderId") String pgOrderId);

    Optional<ChargeRequest> findByPgPaymentKey(String pgPaymentKey);

    boolean existsByMemberIdAndStatusIn(Long memberId, List<ChargeStatus> statuses);

    List<ChargeRequest> findByMemberIdAndStatus(Long memberId, ChargeStatus status);

    List<ChargeRequest> findByStatus(ChargeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChargeRequest c WHERE c.id = :id")
    Optional<ChargeRequest> findByIdForUpdate(@Param("id") Long id);
}
