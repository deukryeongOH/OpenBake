package com.openbake.payment.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record ChargeCreateRequest(
        @Schema(description = "충전 금액 (1,000원 단위, 최소 1,000원, 최대 500,000원)", example = "50000")
        BigDecimal amount
) {
}
