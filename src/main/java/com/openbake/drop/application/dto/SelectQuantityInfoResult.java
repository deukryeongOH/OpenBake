package com.openbake.drop.application.dto;

import com.openbake.drop.domain.EntryStatus;

public record SelectQuantityInfoResult(int selectQuantity, EntryStatus status) {
    public static SelectQuantityInfoResult of(int selectQuantity, EntryStatus status) {
        return new SelectQuantityInfoResult(selectQuantity, status);
    }
}
