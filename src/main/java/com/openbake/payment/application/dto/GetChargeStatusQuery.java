package com.openbake.payment.application.dto;

public record GetChargeStatusQuery(
        Long chargeRequestId,
        Long memberId
) {}
