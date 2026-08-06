package com.openbake.seller.application;

import com.openbake.seller.domain.ApplicationStatus;

import java.time.LocalDateTime;

public record ApplicationStatusUpdateResult(
        Long sellerId,
        ApplicationStatus applicationStatus,
        String rejectReason,
        LocalDateTime updatedAt
) {}
