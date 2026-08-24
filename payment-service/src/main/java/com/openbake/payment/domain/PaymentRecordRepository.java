package com.openbake.payment.domain;

import java.util.Optional;

public interface PaymentRecordRepository {
    Optional<PaymentRecord> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentRecord> findByIdempotencyKeyForUpdate(String idempotencyKey);
    PaymentRecord save(PaymentRecord record);
    PaymentRecord saveAndFlush(PaymentRecord record);
}
