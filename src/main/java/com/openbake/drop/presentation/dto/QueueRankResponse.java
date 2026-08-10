package com.openbake.drop.presentation.dto;

import com.openbake.drop.application.dto.QueueRankResult;

public record QueueRankResponse(
        Long rank,
        String status
) {
    public static QueueRankResponse of(QueueRankResult result) {
        return new QueueRankResponse(result.rank(), result.status());
    }
}
