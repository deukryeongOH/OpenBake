package com.openbake.drop.application.service;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.dto.*;
import com.openbake.drop.application.cache.DropCacheInvalidatedEvent;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.application.port.CurrentSellerPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.domain.*;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.repository.DropRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DropService {

    private final DropRepository dropRepository;
    private final TodayDropCache todayDropCache;
    private final CurrentSellerPort currentSellerPort;
    private final ProductPort productPort;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public DropInfoResult registerDrop(DropInfoCommand command) {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        // 하루 5개 제한 검증 & 시간대 검증
        validateFiveDropPerDay(sellerId, command.dropStart());
        // 제한 수량, 총 수량 검증
        validateLimitQuantityWithTotalQuantity(command.limitQuantity(), command.totalQuantity());

        DropInfoResult result = productPort.registerProduct(command);

        Drop drop = createDrop(result);

        dropRepository.save(drop);

        // 자정 캐시가 오늘 새로 등록된 드롭을 놓치지 않도록 즉시 갱신
        todayDropCache.refresh();
        // 이 요청을 받지 않은 다른 Pod에게도 갱신을 알린다(커밋 후 전파. docs/11번 문서)
        applicationEventPublisher.publishEvent(new DropCacheInvalidatedEvent());

        // productPort.registerProduct 시점엔 Drop이 아직 없어 dropId가 비어 있었다. 저장 후 채워 돌려준다.
        return DropInfoResult.of(result.dropStart(), result.dropEnd(), result.limitQuantity(), result.dropStatus(),
                result.name(), result.description(), result.imageUrl(), result.pickUpAvailableDates(), result.price(),
                result.totalQuantity(), result.remainQuantity(), result.sellerId(), result.productId(), drop.getId(), result.category());
    }

    private Drop createDrop(DropInfoResult result) {
        return Drop.builder()
                .dropStatus(DropStatus.UPCOMING)
                .productId(result.productId())
                .limitQuantity(result.limitQuantity())
                .dropStart(result.dropStart())
                .dropEnd(result.dropEnd())
                .build();
    }


    private void validateLimitQuantityWithTotalQuantity(@Positive(message = "1인당 제한 수량은 1개 이상이어야 합니다.") int limitQuantity, @Positive(message = "총 수량은 0보다 커야 합니다.") int totalQuantity) {
        if (limitQuantity > totalQuantity) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY_LIMIT);
        }
    }

    private void validateFiveDropPerDay(Long sellerId, @NotNull(message = "시작 시간을 입력해주세요.") LocalDateTime dropStart) {
        LocalDate dropDate = dropStart.toLocalDate();
        List<Drop> dropList = dropRepository.findListByDropDate(dropDate);

        validateSlotCapacity(dropList, dropStart);

        boolean alreadyRegisteredToday = dropList.stream()
                .anyMatch(drop -> productPort.getSellerIdByProductId(drop.getProductId()).equals(sellerId));

        if (alreadyRegisteredToday) {
            throw new BusinessException(ErrorCode.DUPLICATE_DROP_DATE);
        }
    }


    private void validateFiveDropPerDayExcludingSelf(Long dropId, Long sellerId, LocalDateTime dropStart) {
        LocalDate dropDate = dropStart.toLocalDate();

        // 오늘 진행하는 드롭 중 현재 수정을 원하는 Drop의 Id와 같지 않은 것들의 드롭 리스트
        List<Drop> dropList = dropRepository.findListByDropDate(dropDate).stream()
                .filter(drop -> !drop.getId().equals(dropId))
                .toList();

        validateSlotCapacity(dropList, dropStart);

        // 이미 판매자가 등록한 날짜로 수정하려고 하면 막아야함. (판매자는 하루에 1개의 드롭만 등록할 수 있다)
        boolean alreadyRegisteredToday = dropList.stream()
                .anyMatch(drop -> productPort.getSellerIdByProductId(drop.getProductId()).equals(sellerId));

        if (alreadyRegisteredToday) {
            throw new BusinessException(ErrorCode.DUPLICATE_DROP_DATE);
        }
    }

    // 하루 5개 Capacity + 같은 시각(슬롯) 중복 체크
    private void validateSlotCapacity(List<Drop> dropList, LocalDateTime dropStart) {
        if (dropList.size() >= 5) {
            throw new BusinessException(ErrorCode.DUPLICATE_DROP_DATE);
        }

        // 오늘 진행하는 드롭들의 시작 시간들을 뽑아서 등록하려는 dropStart와 맞는 것이 있는지 확인
        boolean isNotAvailableSlot = dropList.stream()
                .map(drop -> drop.getDropStart().toLocalTime())
                .anyMatch(dropStart.toLocalTime()::equals);

        if (isNotAvailableSlot) {
            throw new BusinessException(ErrorCode.DUPLICATE_DROP_DATE);
        }
    }

    // 아래 세 전환은 모두 조건부 UPDATE라 여러 번 호출해도 안전하다.
    // 전환이 실제로 일어났는지 알아야 하는 호출자를 위해 영향 행 수를 그대로 돌려준다.
    @Transactional
    public int changeDropStatusActive(Long dropId) {
        return dropRepository.activeStatus(dropId);
    }

    @Transactional
    public int changeDropStatusCompleted(Long dropId) {
        return dropRepository.completeStatus(dropId);
    }

    // 품절로 마감된 드롭을 되살린다. COMPLETED가 아니면 아무 일도 하지 않는다.
    @Transactional
    public int reviveFromSoldOut(Long dropId) {
        return dropRepository.reviveFromSoldOut(dropId);
    }

    // 재고 확정: 드롭당 정확히 1회만 실행되도록 조건부 UPDATE로 막는다. 이미 확정돼 있었으면 false.
    @Transactional
    public boolean markStockFinalized(Long dropId) {
        return dropRepository.markStockFinalized(dropId, LocalDateTime.now()) > 0;
    }

    // 재고 확정 유예 시간이 지났는데 아직 확정 안 된 드롭들. DropStockFinalizeScheduler가 호출한다.
    @Transactional(readOnly = true)
    public List<Drop> findStockFinalizationCandidates(LocalDateTime cutoff) {
        return dropRepository.findStockFinalizationCandidates(cutoff);
    }

    private Drop findDrop(Long dropId) {
        return dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));
    }

    // pickUpAvailableDate가 LAZY 컬렉션이라, 세션이 열려 있는 이 트랜잭션 안에서
    // 새 HashSet으로 복사해둬야 한다. 그냥 참조만 넘기면 세션이 끝난 뒤(JSON 직렬화 시점)
    // 초기화를 시도하다 LazyInitializationException이 난다.
    @Transactional(readOnly = true)
    public DropInfoResult getDropInfo(Long dropId) {
        Drop findDrop = findDrop(dropId);
        DropProductInfoResult result = productPort.getProductInfo(findDrop.getProductId());
        return DropInfoResult.of(findDrop.getDropStart(), findDrop.getDropEnd(), findDrop.getLimitQuantity(), findDrop.getDropStatus(),
                result.name(), result.description(), result.imageUrl(), result.pickUpAvailableDates(), result.price(),
                result.totalQuantity(), result.remainQuantity(), result.sellerId(), result.productId(), findDrop.getId(), result.category());
    }

    // 판매자 본인이 등록한 드롭 목록 조회
    @Transactional(readOnly = true)
    public List<DropInfoResult> getMyDrops() {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        List<DropProductInfoResult> dropProductInfoResultList = productPort.findProductListBySellerId(sellerId);
        List<DropInfoResult> dropInfoResultList = new ArrayList<>();

        for (DropProductInfoResult result : dropProductInfoResultList) {
            if (productPort.isGeneralProduct(result.productId())) {
                continue;
            }
            Drop findDrop = dropRepository.findByProductId(result.productId());
            dropInfoResultList.add(DropInfoResult.of(findDrop.getDropStart(), findDrop.getDropEnd(), findDrop.getLimitQuantity(), findDrop.getDropStatus(),
                    result.name(), result.description(), result.imageUrl(), result.pickUpAvailableDates(), result.price(), result.totalQuantity(),
                    result.remainQuantity(), result.sellerId(), result.productId(), findDrop.getId(), result.category()));
        }

        return dropInfoResultList;
    }

    // 오늘부터 days일 동안 예정된(UPCOMING/ACTIVE) 드롭 목록 조회. dropStart 오름차순.
    @Transactional(readOnly = true)
    public List<DropInfoResult> getUpcomingDrops(int days) {
        validateDays(days);

        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(days).atTime(LocalTime.MAX);

        return dropRepository.findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
                        List.of(DropStatus.UPCOMING, DropStatus.ACTIVE),
                        from,
                        to
                ).stream()
                .map(drop -> {
                    DropProductInfoResult result = productPort.getProductInfo(drop.getProductId());
                    return DropInfoResult.of(drop.getDropStart(), drop.getDropEnd(), drop.getLimitQuantity(), drop.getDropStatus(),
                            result.name(), result.description(), result.imageUrl(), result.pickUpAvailableDates(), result.price(), result.totalQuantity(),
                            result.remainQuantity(), result.sellerId(), result.productId(), drop.getId(), result.category());
                })
                .toList();
    }

    private void validateDays(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("days는 0보다 커야 합니다.");
        }
    }

    // 판매자 본인의 드롭 수정 (시작 전인 드롭만 가능)
    @Transactional
    public DropInfoResult updateDropProduct(Long dropId, DropInfoCommand command) {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        Drop drop = findDrop(dropId);

        validateOwner(drop, sellerId);
        validateEditable(drop);
        validateFiveDropPerDayExcludingSelf(dropId, sellerId, command.dropStart());
        validateLimitQuantityWithTotalQuantity(command.limitQuantity(), command.totalQuantity());

        DropProductInfoResult result = productPort.updateProduct(drop.getProductId(), command);
        drop.update(result.productId(), command.limitQuantity(), command.dropStart(), command.dropEnd());

        dropRepository.save(drop);
        // 오늘 드롭의 시작/마감 시각이 바뀌었을 수 있으므로 캐시를 즉시 갱신
        todayDropCache.refresh();
        applicationEventPublisher.publishEvent(new DropCacheInvalidatedEvent());

        return DropInfoResult.of(drop.getDropStart(), drop.getDropEnd(), drop.getLimitQuantity(), drop.getDropStatus(),
                result.name(), result.description(), result.imageUrl(), result.pickUpAvailableDates(), result.price(),
                result.totalQuantity(), result.remainQuantity(), result.sellerId(), result.productId(), drop.getId(), result.category());
    }

    // 판매자 본인의 드롭 삭제 (시작 전인 드롭만 가능)
    @Transactional
    public void deleteProduct(Long dropId) {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        Drop drop = findDrop(dropId);
        validateOwner(drop, sellerId);
        validateEditable(drop);

        productPort.deleteDropProduct(drop.getProductId());
        dropRepository.delete(drop);

        // 삭제된 드롭이 캐시에 남아 스케줄러가 존재하지 않는 드롭을 참조하지 않도록 즉시 갱신
        todayDropCache.refresh();
        applicationEventPublisher.publishEvent(new DropCacheInvalidatedEvent());
    }

    private void validateOwner(Drop drop, Long sellerId) {
        if (!productPort.getSellerIdByProductId(drop.getProductId()).equals(sellerId)) {
            throw new BusinessException(ErrorCode.DROP_OWNER_MISMATCH);
        }
    }

    private void validateEditable(Drop drop) {
        if (!drop.isEditable()) {
            throw new BusinessException(ErrorCode.DROP_NOT_EDITABLE);
        }
    }

    // date를 받아서 해당 날짜에 등록된 드롭들의 시작 시간을 확인하고, 겹치지 않는 시간대를 반환
    @Transactional(readOnly = true)
    public List<TimeSlotResult> getAvailableSlots(LocalDateCommand command) {
        List<Drop> drops = dropRepository.findListByDropDate(command.todayDate());

        Set<LocalTime> occupiedStartTimes = drops.stream()
                .map(drop -> drop.getDropStart().toLocalTime())
                .collect(Collectors.toSet());

        return Arrays.stream(DropTimeSlot.values())
                .filter(slot -> !occupiedStartTimes.contains(slot.getStart()))
                .map(TimeSlotResult::of)
                .toList();
    }
}
