package com.openbake.seller.application;

import java.time.LocalDateTime;

public record BusinessVerificationResult(
        boolean verified,
        String businessNumber,
        LocalDateTime verifiedAt
) {}
