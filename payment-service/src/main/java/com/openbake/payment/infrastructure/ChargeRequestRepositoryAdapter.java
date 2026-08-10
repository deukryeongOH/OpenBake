package com.openbake.payment.infrastructure;

import com.openbake.payment.domain.ChargeRequest;
import com.openbake.payment.domain.ChargeRequestRepository;
import com.openbake.payment.domain.ChargeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChargeRequestRepositoryAdapter implements ChargeRequestRepository {

    private final ChargeRequestJpaRepository jpaRepository;

    @Override
    public ChargeRequest save(ChargeRequest chargeRequest) {
        return jpaRepository.save(chargeRequest);
    }

    @Override
    public Optional<ChargeRequest> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<ChargeRequest> findByPgOrderIdForUpdate(String pgOrderId) {
        return jpaRepository.findByPgOrderIdForUpdate(pgOrderId);
    }

    @Override
    public Optional<ChargeRequest> findByPgPaymentKey(String pgPaymentKey) {
        return jpaRepository.findByPgPaymentKey(pgPaymentKey);
    }

    @Override
    public Optional<ChargeRequest> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id);
    }

    @Override
    public boolean existsByMemberIdAndStatusIn(Long memberId, List<ChargeStatus> statuses) {
        return jpaRepository.existsByMemberIdAndStatusIn(memberId, statuses);
    }

    @Override
    public List<ChargeRequest> findByMemberIdAndStatus(Long memberId, ChargeStatus status) {
        return jpaRepository.findByMemberIdAndStatus(memberId, status);
    }

    @Override
    public List<ChargeRequest> findByStatus(ChargeStatus status) {
        return jpaRepository.findByStatus(status);
    }
}
