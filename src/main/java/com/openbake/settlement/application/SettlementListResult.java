package com.openbake.settlement.application;

import java.util.List;

public record SettlementListResult(
        List<SettlementResult> content,
        int page,
        int size,
        boolean hasNext
) {
}
