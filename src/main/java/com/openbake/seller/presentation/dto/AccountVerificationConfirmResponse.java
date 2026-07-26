package com.openbake.seller.presentation.dto;

import java.time.LocalDateTime;

public record AccountVerificationConfirmResponse(
        boolean verified,
        LocalDateTime accountVerifiedAt
) {}
