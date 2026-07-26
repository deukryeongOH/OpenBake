package com.openbake.seller.presentation.dto;

import java.time.LocalDateTime;

public record BusinessVerificationResponse(
        boolean verified,
        String businessNumber,
        LocalDateTime verifiedAt
) {}
