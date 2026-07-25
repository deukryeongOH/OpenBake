package com.openbake.seller.presentation.dto;

import com.openbake.seller.domain.ApplicationStatus;

public record ApplicationCreateResponse(
        Long sellerId,
        Long memberId,
        String bakeryName,
        ApplicationStatus applicationStatus
) {}
