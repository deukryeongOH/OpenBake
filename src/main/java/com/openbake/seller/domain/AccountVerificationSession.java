package com.openbake.seller.domain;

import java.time.LocalDateTime;

public record AccountVerificationSession(
        Long memberId,
        String bankCode,
        String accountNumber,
        String accountHolder,
        String code,
        LocalDateTime expiresAt
) {}
