package com.openbake.drop.application.dto;

import com.openbake.drop.domain.DropTimeSlot;

public record TimeSlotResult(DropTimeSlot slot) {
    public static TimeSlotResult of(DropTimeSlot slot) {
        return new TimeSlotResult(slot);
    }
}