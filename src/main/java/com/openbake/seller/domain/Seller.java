package com.openbake.seller.domain;

import com.openbake.common.exception.InvalidApplicationStatusException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "sellers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long memberId;

    @Column(nullable = false)
    private String bakeryName;

    @Column(name = "business_number", nullable = false)
    private String businessNumber;

    @Column(name = "business_address", nullable = false)
    private String businessAddress;

    @Column(name = "business_representative_name", nullable = false)
    private String businessRepresentativeName;


    @Column(name = "business_verified", nullable = false)
    private boolean businessVerified;

    @Column(name = "business_verified_at")
    private LocalDateTime businessVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false)
    private ApplicationStatus applicationStatus;

    @Column(name = "settlement_bank_code", nullable = false)
    private String settlementBankCode;

    @Convert(converter = SettlementAccountConverter.class)
    @Column(name = "settlement_account_number", nullable = false)
    private String settlementAccountNumber;

    @Convert(converter = SettlementAccountConverter.class)
    @Column(name = "settlement_account_holder", nullable = false)
    private String settlementAccountHolder;

    @Column(name = "account_verified", nullable = false)
    private boolean accountVerified;

    @Column(name = "account_verified_at")
    private LocalDateTime accountVerifiedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Seller(Long memberId, String bakeryName, String businessNumber, String businessAddress,
                  String businessRepresentativeName, boolean businessVerified, String settlementBankCode,
                  String settlementAccountNumber, String settlementAccountHolder, boolean accountVerified) {
        this.memberId = memberId;
        this.bakeryName = bakeryName;
        this.businessNumber = businessNumber;
        this.businessAddress = businessAddress;
        this.businessRepresentativeName = businessRepresentativeName;
        this.businessVerified = businessVerified;
        if (businessVerified) {
            this.businessVerifiedAt = LocalDateTime.now();
        }
        this.applicationStatus = ApplicationStatus.PENDING;
        this.settlementBankCode = settlementBankCode;
        this.settlementAccountNumber = settlementAccountNumber;
        this.settlementAccountHolder = settlementAccountHolder;
        this.accountVerified = accountVerified;
        if (accountVerified) {
            this.accountVerifiedAt = LocalDateTime.now();
        }
    }

    public void approve() {
        if (applicationStatus != ApplicationStatus.PENDING) {
            throw new InvalidApplicationStatusException();
        }
        this.applicationStatus = ApplicationStatus.APPROVED;
    }

    public void reject(String rejectReason) {
        if (applicationStatus != ApplicationStatus.PENDING) {
            throw new InvalidApplicationStatusException();
        }
        this.applicationStatus = ApplicationStatus.REJECTED;
        this.rejectReason = rejectReason;
    }
}
