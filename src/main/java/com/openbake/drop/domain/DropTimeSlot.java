package com.openbake.drop.domain;

import java.time.LocalTime;

public enum DropTimeSlot {
    SLOT_1(LocalTime.of(9, 0), LocalTime.of(10, 0)),
    SLOT_2(LocalTime.of(11, 0), LocalTime.of(12, 0)),
    SLOT_3(LocalTime.of(13, 0), LocalTime.of(14, 0)),
    SLOT_4(LocalTime.of(15, 0), LocalTime.of(16, 0)),
    SLOT_5(LocalTime.of(17, 0), LocalTime.of(18, 0));

    private final LocalTime start;
    private final LocalTime end;

    DropTimeSlot(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalTime getStart(){
        return this.start;
    }

    public LocalTime getEnd(){
        return this.end;
    }
}
