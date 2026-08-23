package com.openbake.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_records")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentRecordStatus status;

    @Column
    private String failReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected PaymentRecord() {}

    public static PaymentRecord success(String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {
        PaymentRecord record = new PaymentRecord();
        record.idempotencyKey = idempotencyKey;
        record.orderId = orderId;
        record.memberId = memberId;
        record.amount = amount;
        record.status = PaymentRecordStatus.SUCCESS;
        record.createdAt = LocalDateTime.now();
        return record;
    }

    public static PaymentRecord fail(String idempotencyKey, Long orderId, Long memberId, BigDecimal amount, String failReason) {
        PaymentRecord record = new PaymentRecord();
        record.idempotencyKey = idempotencyKey;
        record.orderId = orderId;
        record.memberId = memberId;
        record.amount = amount;
        record.status = PaymentRecordStatus.FAIL;
        record.failReason = failReason;
        record.createdAt = LocalDateTime.now();
        return record;
    }

    public boolean isSuccess() {
        return status == PaymentRecordStatus.SUCCESS;
    }

    public boolean isFail() {
        return status == PaymentRecordStatus.FAIL;
    }

    public void markSuccess() {
        this.status = PaymentRecordStatus.SUCCESS;
        this.failReason = null;
    }

    public void markFail(String failReason) {
        if (this.status == PaymentRecordStatus.SUCCESS) {
            return;
        }
        this.status = PaymentRecordStatus.FAIL;
        this.failReason = failReason;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getOrderId() { return orderId; }
    public Long getMemberId() { return memberId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentRecordStatus getStatus() { return status; }
    public String getFailReason() { return failReason; }
}
