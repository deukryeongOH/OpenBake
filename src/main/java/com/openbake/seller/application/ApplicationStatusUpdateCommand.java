package com.openbake.seller.application;

import com.openbake.seller.domain.ApplicationStatus;

public record ApplicationStatusUpdateCommand(
        ApplicationStatus applicationStatus,
        String rejectReason
) {}
