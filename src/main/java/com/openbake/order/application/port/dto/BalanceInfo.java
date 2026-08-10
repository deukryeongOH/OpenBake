package com.openbake.order.application.port.dto;

import java.math.BigDecimal;

public record BalanceInfo(Long memberId, BigDecimal balance) {
}
