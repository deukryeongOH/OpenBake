package com.openbake.seller.application;

import java.time.LocalDateTime;

public record AccountVerificationCodeResult(
        String verificationRequestId,
        String code,
        LocalDateTime expiresAt
) {}
