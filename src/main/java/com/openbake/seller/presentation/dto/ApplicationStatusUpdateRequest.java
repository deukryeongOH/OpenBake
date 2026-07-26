package com.openbake.seller.presentation.dto;

import com.openbake.seller.domain.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record ApplicationStatusUpdateRequest(
        @NotNull ApplicationStatus applicationStatus,
        String rejectReason
) {}
