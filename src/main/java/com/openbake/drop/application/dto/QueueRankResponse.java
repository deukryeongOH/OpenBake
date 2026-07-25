package com.openbake.drop.application.dto;

public record QueueRankResponse(
        Long rank,
        String status
) {
    public static QueueRankResponse of(Long rank) {
        if (rank == 0) {
            return new QueueRankResponse(0L, "ACTIVE");
        }
        if (rank == -1) {
            return new QueueRankResponse(-1L, "NOT_FOUND");
        }
        return new QueueRankResponse(rank, "WAITING");
    }
}
