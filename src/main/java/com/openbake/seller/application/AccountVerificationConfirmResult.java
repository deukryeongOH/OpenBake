package com.openbake.seller.application;

import java.time.LocalDateTime;

public record AccountVerificationConfirmResult(
        boolean verified,
        LocalDateTime accountVerifiedAt
) {}
