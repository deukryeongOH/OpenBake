package com.openbake.settlement.presentation;

import com.openbake.settlement.application.SettlementListResult;

import java.util.List;

public record SettlementListResponse(
        List<SettlementResponse> content,
        int page,
        int size,
        boolean hasNext
) {

    public static SettlementListResponse from(
            SettlementListResult result
    ) {
        List<SettlementResponse> content = result.content()
                .stream()
                .map(SettlementResponse::from)
                .toList();

        return new SettlementListResponse(
                content,
                result.page(),
                result.size(),
                result.hasNext()
        );
    }
}
