package com.openbake.seller.application;

import java.time.LocalDateTime;

public record AccountVerificationStartResult(
        String verificationRequestId,
        LocalDateTime expiresAt
) {}
