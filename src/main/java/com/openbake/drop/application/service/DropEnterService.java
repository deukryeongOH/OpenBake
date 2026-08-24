package com.openbake.drop.application.service;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.application.dto.ConfirmEntryResult;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.port.StockReservationPort;
import com.openbake.drop.domain.*;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.entity.DropEntry;
import com.openbake.drop.domain.repository.DropEntryRepository;
import com.openbake.drop.domain.repository.DropRepository;
import com.openbake.drop.application.port.CurrentMemberPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class DropEnterService {

    // 이미 재고를 선점했거나(RESERVED) 구매까지 완료한(COMPLETED) 유저만 재입장을 막는다.
    // ENTERED는 재고를 붙잡지 않는 상태라(입장 후 상세만 보고 나간 경우 포함) 재진입을 막을 이유가 없다.
    private static final List<EntryStatus> BLOCK_STATUSES = List.of(EntryStatus.RESERVED, EntryStatus.COMPLETED);

    // confirmEntry는 isAccessible()이 dropStatus(가변)를 봐야 해서 DB 조회를 캐시로 대체할 수 없다.
    // 품절로 COMPLETED가 된 드롭을 걸러내는 것이 이 검증의 핵심이라 낡은 값을 쓸 수 없다.
    private final DropEntryRepository dropEntryRepository;
    private final DropRepository dropRepository;
    private final CurrentMemberPort currentMemberPort;
    private final ProductPort productPort;
    private final TodayDropCache todayDropCache;
    private final StockReservationPort stockReservationPort;


    @Transactional(readOnly = true)
    public List<Long> getTodayDropIds() {
        LocalDate today = LocalDate.now();

        List<Drop> findDrop = dropRepository.findListByDropDate(today);

        if (findDrop.isEmpty()) {
            return List.of();
        }

        return findDrop.stream().map(Drop::getId).toList();
    }

    /**
     * 드롭 입장 확정.
     *
     * 대기열이 있던 시절에는 진입 경로가 enterQueue -> confirmEntry 2단계였고,
     * 시간 검증과 중복 참여 차단은 enterQueue가, 대기열 통과 여부(isActive)는 confirmEntry가 맡았다.
     * 대기열을 걷어내면서 입장 경로가 이 메서드 하나로 줄었으므로 앞단 검증 두 개를 여기로 옮긴다.
     * 이 검증이 없으면 드롭 기간 밖이거나 이미 구매를 마친 유저도 ENTERED를 만들 수 있고,
     * ENTERED는 곧바로 재고 선점(DropLockService.decreaseQuantity)의 통과 조건이 된다.
     */
    @Transactional
    public ConfirmEntryResult confirmEntry(Long dropId) {
        Long memberId = currentMemberPort.getCurrentMemberId();

        Drop findDrop = dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));

        // 진행 시간 검증 (품절로 COMPLETED가 된 드롭도 여기서 걸러진다)
        if (!findDrop.isAccessible(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.DROP_NOT_ACTIVE);
        }

        // (drop_id, member_id)에 유니크 제약(uk_drop_member)이 있어 행이 많아야 하나다.
        // 한 번만 읽어 중복 참여 차단 / 재입장 / 신규 생성 판단에 모두 재사용한다.
        DropEntry existingEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId).orElse(null);

        if (existingEntry != null && BLOCK_STATUSES.contains(existingEntry.getEntryStatus())) {
            throw new BusinessException(ErrorCode.ALREADY_ENTERED, "이미 참여 중이거나 구매가 완료된 드롭입니다.");
        }

        ConfirmEntryResult confirmEntryResult = buildResult(dropId, findDrop);

        // 대기열이 있을 때는 isActive 게이트가 입장 확정을 1회로 소진시켰지만(성공 직후 removeActiveUser),
        // 대기열을 걷어내면서 같은 유저가 몇 번이든 호출할 수 있게 됐다.
        // 이미 ENTERED면 바꿀 상태가 없으므로 쓰기 없이 끝낸다.
        if (existingEntry != null && existingEntry.getEntryStatus() == EntryStatus.ENTERED) {
            return confirmEntryResult;
        }

        if (existingEntry != null) {
            existingEntry.changeStatusEnter(); // 유니크 제약 때문에 새로 만들지 않고 상태만 되돌린다
            dropEntryRepository.save(existingEntry);
        } else {
            dropEntryRepository.save(DropEntry.createInitialEntry(dropId, memberId));
        }

        return confirmEntryResult;
    }

    /**
     * 응답 조립. 정상 경로에서는 DB를 읽지 않는다.
     *
     * 상품 표시 정보(이름·설명·이미지·가격·픽업일)는 드롭 중 불변이라 TodayDropCache에서 읽고,
     * 잔여 수량은 드롭 중 정본인 Redis 카운터에서 읽는다. 기존에는 이 하나를 위해
     * getProductInfo가 상품·재고·픽업일 지연 컬렉션까지 3개 쿼리를 태우고 있었다.
     *
     * 캐시나 카운터가 없으면(오늘 드롭이 아니거나 Redis 유실) 기존 DB 경로로 폴백한다.
     * 이 경로는 응답을 만들 뿐 재고를 건드리지 않으므로, 거부하기보다 느리더라도 응답하는 편이 낫다.
     */
    private ConfirmEntryResult buildResult(Long dropId, Drop findDrop) {
        Long remain = stockReservationPort.peek(dropId);

        if (remain != null) {
            Optional<CachedDrop> cached = todayDropCache.find(dropId);
            if (cached.isPresent()) {
                return ConfirmEntryResult.of(cached.get(), remain.intValue());
            }
        }

        DropProductInfoResult result = productPort.getProductInfo(findDrop.getProductId());
        return ConfirmEntryResult.of(result, findDrop.getLimitQuantity());
    }

    /**
     * 마감된 드롭에서 ENTERED로 남은 진입 내역을 일괄 실패 처리한다.
     *
     * 대기열이 있을 때는 active 슬롯(10분 TTL)을 회수하려고 2분마다 폴링했지만,
     * 대기열이 사라져 회수할 슬롯 자체가 없다. 남은 목적은 "입장만 하고 구매하지 않음"을 이력에 남기는 것뿐이라
     * 드롭당 UPDATE 1회로 마감 시점에 한 번만 정리한다.
     */
    @Transactional
    public int expireRemainingEntries(Long dropId) {
        return dropEntryRepository.expireEnteredEntries(dropId);
    }
}