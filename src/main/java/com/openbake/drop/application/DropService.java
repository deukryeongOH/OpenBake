package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.domain.*;
import com.openbake.drop.presentation.dto.DropProductInfoRequest;
import com.openbake.drop.application.dto.DropProductInfoResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class DropService {

    private final DropRepository dropRepository;
    private final DropInventoryRepository dropInventoryRepository;

    @Transactional
    public DropProductInfoResponse registerDropProduct(DropProductInfoRequest request, Long sellerId) {
        // 하루 1개 제한 검증
        validateOneDropPerDay(sellerId, request.dropStart());
        // 제한 수량, 총 수량 검증
        validateLimitQuantityWithTotalQuantity(request.limitQuantity(), request.totalQuantity());

        DropProduct dropProduct = createDropProduct(request);

        Drop drop = createDrop(request, dropProduct, sellerId);

        Drop savedDrop = dropRepository.save(drop);

        DropInventory dropInventory = createDropInventory(savedDrop, request);

        DropInventory savedDropInventory = dropInventoryRepository.save(dropInventory);

        return DropProductInfoResponse.of(savedDrop, savedDropInventory);
    }


    private DropInventory createDropInventory(Drop savedDrop, DropProductInfoRequest dropProductInfoRequest) {
        return DropInventory.builder()
                .dropId(savedDrop.getId())
                .totalQuantity(dropProductInfoRequest.totalQuantity())
                .remainQuantity(dropProductInfoRequest.totalQuantity()) // 처음에는 총 수량 = 남은 수량
                .build();
    }


    private Drop createDrop(DropProductInfoRequest dropProductInfoRequest, DropProduct dropProduct, Long sellerId) {
        return Drop.builder()
                .dropStatus(DropStatus.UPCOMING)
                .pickUpAvailableDates(dropProductInfoRequest.pickUpAvailableDates())
                .dropProduct(dropProduct)
                .limitQuantity(dropProductInfoRequest.limitQuantity())
                .dropStart(dropProductInfoRequest.dropStart())
                .dropEnd(dropProductInfoRequest.dropEnd())
                .sellerId(sellerId)
                .build();
    }

    private DropProduct createDropProduct(DropProductInfoRequest dropProductInfoRequest) {
        return DropProduct.builder()
                .name(dropProductInfoRequest.name())
                .description(dropProductInfoRequest.description())
                .imageUrl(dropProductInfoRequest.imageUrl())
                .price(dropProductInfoRequest.price())
                .build();
    }


    private void validateLimitQuantityWithTotalQuantity(@Positive(message = "1인당 제한 수량은 1개 이상이어야 합니다.") int limitQuantity, @Positive(message = "총 수량은 0보다 커야 합니다.") int totalQuantity) {
        if (limitQuantity > totalQuantity) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY_LIMIT);
        }
    }

    private void validateOneDropPerDay(Long sellerId, @NotNull(message = "시작 시간을 입력해주세요.") LocalDateTime dropStart) {
        LocalDate dropDate = dropStart.toLocalDate();
        LocalDateTime startOfDay = dropDate.atStartOfDay();
        LocalDateTime endOfDay = dropDate.atTime(LocalTime.MAX);

        if (dropRepository.existsBySellerIdAndDropStartBetween(sellerId, startOfDay, endOfDay)) {
            throw new BusinessException(ErrorCode.DUPLICATE_DROP_DATE); // D004, HTTP 409 반환
        }
    }
    @Transactional
    public void changeDropStatusActive(Long dropId) {
        Drop drop = findDrop(dropId);
        drop.changeStatus(DropStatus.ACTIVE);
    }

    @Transactional
    public void changeDropStatusCompleted(Long dropId) {
        Drop drop = findDrop(dropId);
        drop.changeStatus(DropStatus.COMPLETED);
    }

    private Drop findDrop(Long dropId){
        return dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));
    }
}
