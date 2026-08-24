package com.openbake.payment.infrastructure;

import com.openbake.payment.domain.PaymentRecord;
import com.openbake.payment.domain.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRecordRepositoryAdapter implements PaymentRecordRepository {

    private final PaymentRecordJpaRepository jpaRepository;

    @Override
    public Optional<PaymentRecord> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Optional<PaymentRecord> findByIdempotencyKeyForUpdate(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKeyForUpdate(idempotencyKey);
    }

    @Override
    public PaymentRecord save(PaymentRecord record) {
        return jpaRepository.save(record);
    }

    @Override
    public PaymentRecord saveAndFlush(PaymentRecord record) {
        return jpaRepository.saveAndFlush(record);
    }
}
