package com.openbake.drop.presentation.dto;

import com.openbake.drop.application.dto.TimeSlotResult;

import java.time.LocalTime;
import java.util.List;

public record TimeSlotResponse(String slot, LocalTime start, LocalTime end) {
    public static TimeSlotResponse of(TimeSlotResult result) {
        return new TimeSlotResponse(result.slot().name(), result.slot().getStart(), result.slot().getEnd());
    }

    public static List<TimeSlotResponse> of(List<TimeSlotResult> results) {
        return results.stream().map(TimeSlotResponse::of).toList();
    }
}