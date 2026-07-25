package com.openbake.seller.presentation.dto;

import com.openbake.seller.domain.ApplicationStatus;

import java.time.LocalDateTime;

public record SellerResponse(
        Long sellerId,
        Long memberId,
        String bakeryName,
        String businessNumber,
        ApplicationStatus applicationStatus,
        String settlementBankCode,
        String settlementAccountNumberMasked,
        boolean accountVerified,
        LocalDateTime accountVerifiedAt
) {}
