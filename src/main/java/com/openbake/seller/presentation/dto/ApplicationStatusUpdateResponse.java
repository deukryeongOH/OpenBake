package com.openbake.seller.presentation.dto;

import com.openbake.seller.domain.ApplicationStatus;

import java.time.LocalDateTime;

public record ApplicationStatusUpdateResponse(
        Long sellerId,
        ApplicationStatus applicationStatus,
        String rejectReason,
        LocalDateTime updatedAt
) {}
