package com.openbake.seller.presentation.dto;

import java.time.LocalDateTime;

public record AccountVerificationStartResponse(
        String verificationRequestId,
        LocalDateTime expiresAt
) {}
