package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.dto.DropInfoResult;
import com.openbake.drop.application.dto.DropProductInfoCommand;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.queue.TodayDropCache;
import com.openbake.drop.domain.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DropService {

    private final DropRepository dropRepository;
    private final DropInventoryRepository dropInventoryRepository;
    private final TodayDropCache todayDropCache;

    @Transactional
    public DropProductInfoResult registerDropProduct(DropProductInfoCommand command, Long sellerId) {
        // 하루 1개 제한 검증
        validateOneDropPerDay(sellerId, command.dropStart());
        // 제한 수량, 총 수량 검증
        validateLimitQuantityWithTotalQuantity(command.LimitQuantity(), command.totalQuantity());

        DropProduct dropProduct = createDropProduct(command);

        Drop drop = createDrop(command, dropProduct, sellerId);

        Drop savedDrop = dropRepository.save(drop);

        DropInventory dropInventory = createDropInventory(savedDrop, command);

        DropInventory savedDropInventory = dropInventoryRepository.save(dropInventory);

        // 자정 캐시가 오늘 새로 등록된 드롭을 놓치지 않도록 즉시 갱신
        todayDropCache.refresh();

        return DropProductInfoResult.of(savedDrop, savedDropInventory);
    }


    private DropInventory createDropInventory(Drop savedDrop, DropProductInfoCommand command) {
        return DropInventory.builder()
                .dropId(savedDrop.getId())
                .totalQuantity(command.totalQuantity())
                .remainQuantity(command.totalQuantity()) // 처음에는 총 수량 = 남은 수량
                .build();
    }


    private Drop createDrop(DropProductInfoCommand command, DropProduct dropProduct, Long sellerId) {
        return Drop.builder()
                .dropStatus(DropStatus.UPCOMING)
                .pickUpAvailableDates(command.pickupDates())
                .dropProduct(dropProduct)
                .limitQuantity(command.LimitQuantity())
                .dropStart(command.dropStart())
                .dropEnd(command.dropEnd())
                .sellerId(sellerId)
                .build();
    }

    private DropProduct createDropProduct(DropProductInfoCommand command) {
        return DropProduct.builder()
                .name(command.name())
                .description(command.description())
                .imageUrl(command.image())
                .price(command.price())
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

        // 먼저 하루에 드롭은 한 번으로 제한되므로 먼저 검증
        if (dropRepository.existsByDropStartBetween(startOfDay, endOfDay)) {
            throw new BusinessException(ErrorCode.DUPLICATE_DROP_DATE);
        }

        // (확장성을 고려한 판매자 드롭 등록 제한 / 추후 하루에 드롭이 여러 개일 경우)
        if (dropRepository.existsBySellerIdAndDropStartBetween(sellerId, startOfDay, endOfDay)) {
            throw new BusinessException(ErrorCode.DUPLICATE_DROP_DATE);
        }
    }

    // 수정 시 "하루 1개 제한" 재검증. 하루에 드롭이 하나뿐이라, 수정 대상 자기 자신을 빼지 않으면
    // 날짜를 안 바꾸는 수정조차 "이미 그 날짜에 드롭이 있다"고 오판해서 막히므로 자신은 제외하고 확인한다.
    private void validateOneDropPerDayExcludingSelf(Long dropId, Long sellerId, LocalDateTime dropStart) {
        LocalDate dropDate = dropStart.toLocalDate();
        LocalDateTime startOfDay = dropDate.atStartOfDay();
        LocalDateTime endOfDay = dropDate.atTime(LocalTime.MAX);

        if (dropRepository.existsByDropStartBetweenAndIdNot(startOfDay, endOfDay, dropId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_DROP_DATE);
        }

        if (dropRepository.existsBySellerIdAndDropStartBetweenAndIdNot(sellerId, startOfDay, endOfDay, dropId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_DROP_DATE);
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

    // pickUpAvailableDate가 LAZY 컬렉션이라, 세션이 열려 있는 이 트랜잭션 안에서
    // 새 HashSet으로 복사해둬야 한다. 그냥 참조만 넘기면 세션이 끝난 뒤(JSON 직렬화 시점)
    // 초기화를 시도하다 LazyInitializationException이 난다.
    @Transactional(readOnly = true)
    public DropInfoResult getDropProductInfo(Long dropId) {
        Drop findDrop = findDrop(dropId);
        DropInventory dropInventory = dropInventoryRepository.findByDropId(dropId);
        return DropInfoResult.of(findDrop.getDropProduct().getName(), findDrop.getDropProduct().getDescription(),
                findDrop.getDropProduct().getImageUrl(), findDrop.getDropStart(), findDrop.getDropEnd(),
                findDrop.getLimitQuantity(), findDrop.getDropProduct().getPrice(), dropInventory.getTotalQuantity(), dropInventory.getRemainQuantity(), findDrop.getDropStatus(), new HashSet<>(findDrop.getPickUpAvailableDate()));
    }

    // 판매자 본인이 등록한 드롭 목록 조회
    @Transactional(readOnly = true)
    public List<DropProductInfoResult> getMyDrops(Long sellerId) {
        return dropRepository.findAllBySellerId(sellerId).stream()
                .map(drop -> DropProductInfoResult.of(drop, dropInventoryRepository.findByDropId(drop.getId())))
                .toList();
    }

    // 오늘부터 days일 동안 예정된(UPCOMING/ACTIVE) 드롭 목록 조회. dropStart 오름차순.
    @Transactional(readOnly = true)
    public List<DropProductInfoResult> getUpcomingDrops(int days) {
        validateDays(days);

        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(days).atTime(LocalTime.MAX);

        return dropRepository.findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
                        List.of(DropStatus.UPCOMING, DropStatus.ACTIVE),
                        from,
                        to
                ).stream()
                .map(drop -> DropProductInfoResult.of(drop, dropInventoryRepository.findByDropId(drop.getId())))
                .toList();
    }

    private void validateDays(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("days는 0보다 커야 합니다.");
        }
    }

    // 판매자 본인의 드롭 수정 (시작 전인 드롭만 가능)
    @Transactional
    public DropProductInfoResult updateDropProduct(Long dropId, Long sellerId, DropProductInfoCommand command) {
        Drop drop = findDrop(dropId);
        validateOwner(drop, sellerId);
        validateEditable(drop);
        validateOneDropPerDayExcludingSelf(dropId, sellerId, command.dropStart());
        validateLimitQuantityWithTotalQuantity(command.LimitQuantity(), command.totalQuantity());

        DropProduct dropProduct = createDropProduct(command);
        drop.update(dropProduct, command.pickupDates(), command.LimitQuantity(), command.dropStart(), command.dropEnd());

        DropInventory dropInventory = dropInventoryRepository.findByDropId(dropId);
        dropInventory.resetQuantity(command.totalQuantity());

        // 오늘 드롭의 시작/마감 시각이 바뀌었을 수 있으므로 캐시를 즉시 갱신
        todayDropCache.refresh();

        return DropProductInfoResult.of(drop, dropInventory);
    }

    // 판매자 본인의 드롭 삭제 (시작 전인 드롭만 가능)
    @Transactional
    public void deleteDropProduct(Long dropId, Long sellerId) {
        Drop drop = findDrop(dropId);
        validateOwner(drop, sellerId);
        validateEditable(drop);

        dropInventoryRepository.delete(dropInventoryRepository.findByDropId(dropId));
        dropRepository.delete(drop);

        // 삭제된 드롭이 캐시에 남아 스케줄러가 존재하지 않는 드롭을 참조하지 않도록 즉시 갱신
        todayDropCache.refresh();
    }

    private void validateOwner(Drop drop, Long sellerId) {
        if (!drop.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.DROP_OWNER_MISMATCH);
        }
    }

    private void validateEditable(Drop drop) {
        if (!drop.isEditable()) {
            throw new BusinessException(ErrorCode.DROP_NOT_EDITABLE);
        }
    }
}
