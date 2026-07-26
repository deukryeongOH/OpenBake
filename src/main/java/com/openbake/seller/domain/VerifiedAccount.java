package com.openbake.seller.domain;

import java.time.LocalDateTime;

public record VerifiedAccount(
        String bankCode,
        String accountNumber,
        String accountHolder,
        LocalDateTime verifiedAt
) {}
