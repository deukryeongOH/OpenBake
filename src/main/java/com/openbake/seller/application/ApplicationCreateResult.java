package com.openbake.seller.application;

import com.openbake.seller.domain.ApplicationStatus;

public record ApplicationCreateResult(
        Long sellerId,
        Long memberId,
        String bakeryName,
        ApplicationStatus applicationStatus
) {}
