package com.openbake.payment.infrastructure;

import com.openbake.payment.domain.PaymentRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRecordJpaRepository extends JpaRepository<PaymentRecord, Long> {
    Optional<PaymentRecord> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentRecord p WHERE p.idempotencyKey = :idempotencyKey")
    Optional<PaymentRecord> findByIdempotencyKeyForUpdate(
            @Param("idempotencyKey") String idempotencyKey);
}
