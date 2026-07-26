package com.openbake.seller.presentation.dto;

import java.time.LocalDateTime;

public record AccountVerificationCodeResponse(
        String verificationRequestId,
        String code,
        LocalDateTime expiresAt
) {}
