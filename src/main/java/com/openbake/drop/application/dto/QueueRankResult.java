package com.openbake.drop.application.dto;

public record QueueRankResult(
        Long rank,
        String status
) {
    public static QueueRankResult of(Long rank) {
        if (rank == 0) {
            return new QueueRankResult(0L, "ACTIVE");
        }
        if (rank == -1) {
            return new QueueRankResult(-1L, "NOT_FOUND");
        }
        return new QueueRankResult(rank, "WAITING");
    }
}
