package com.openbake.drop.application.dto;

import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropInventory;
import com.openbake.drop.domain.DropStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public record DropProductInfoResponse(
        String name, String description, String imageUrl,
        Set<LocalDate> pickUpAvailableDates,
        LocalDateTime dropStart, LocalDateTime dropEnd,
        int limitQuantity, int price, int totalQuantity, int remainQuantity,
        DropStatus dropStatus,
        Long dropId){

    // pickUpAvailableDate가 LAZY 컬렉션이라, 호출부의 트랜잭션(세션)이 열려 있는 동안
    // 새 HashSet으로 복사해둬야 한다. 참조만 넘기면 세션이 끝난 뒤(JSON 직렬화 시점)
    // 초기화를 시도하다 LazyInitializationException이 난다.
    public static DropProductInfoResponse of(Drop drop, DropInventory inventory) {
        return new DropProductInfoResponse(
                drop.getDropProduct().getName(),
                drop.getDropProduct().getDescription(),
                drop.getDropProduct().getImageUrl(),
                new HashSet<>(drop.getPickUpAvailableDate()),
                drop.getDropStart(),
                drop.getDropEnd(),
                drop.getLimitQuantity(),
                drop.getDropProduct().getPrice(),
                inventory.getTotalQuantity(),
                inventory.getRemainQuantity(),
                drop.getDropStatus(), // UPCOMING 하드코딩 대신 객체 상태 사용
                drop.getId()
        );
    }
}
