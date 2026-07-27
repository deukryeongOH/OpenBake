package com.openbake.order.domain;

//발신함 이벤트 전송 상태. 릴레이가 PENDING 을 읽어 전송하고 SENT/FAILED 로 바꾼다.
public enum OutboxStatus {
    PENDING, SENT, FAILED
}
