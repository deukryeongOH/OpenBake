package com.openbake.drop.presentation.dto;

import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.domain.DropStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public static DropProductInfoResponse of(DropProductInfoResult result) {
        return new DropProductInfoResponse(
                result.name(),
                result.description(),
                result.imageUrl(),
                result.pickUpAvailableDates(),
                result.dropStart(),
                result.dropEnd(),
                result.limitQuantity(),
                result.price(),
                result.totalQuantity(),
                result.remainQuantity(),
                result.dropStatus(),
                result.dropId()
        );
    }
}
