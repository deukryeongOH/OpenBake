package com.openbake.drop.application.service;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.dto.ConfirmEntryResult;
import com.openbake.drop.application.dto.QueueRankResult;
import com.openbake.drop.application.queue.QueueManager;
import com.openbake.drop.domain.*;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.entity.DropEntry;
import com.openbake.drop.domain.entity.DropInventory;
import com.openbake.drop.domain.repository.DropEntryRepository;
import com.openbake.drop.domain.repository.DropInventoryRepository;
import com.openbake.drop.domain.repository.DropRepository;
import com.openbake.drop.infrastructure.port.CurrentMemberPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class DropEnterService {

    private final DropEntryRepository dropEntryRepository;
    private final QueueManager queueManager;
    private final DropInventoryRepository dropInventoryRepository;
    private final DropRepository dropRepository;
    private final CurrentMemberPort currentMemberPort;

    @Transactional(readOnly = true)
    public List<Long> getTodayDropIds() {
        LocalDate today = LocalDate.now();

        List<Drop> findDrop = dropRepository.findListByDropDate(today);

        if (findDrop.isEmpty()) {
            return List.of();
        }

        return findDrop.stream().map(Drop::getId).toList();
    }

    @Transactional(readOnly = true)
    public QueueRankResult enterQueue(Long dropId) {
        Long memberId = currentMemberPort.getCurrentMemberId();

        LocalDateTime now = LocalDateTime.now();

        Drop findDrop = dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));

        // 진행 시간 검증
        if(findDrop.isAccessible(now) == false){
            throw new BusinessException(ErrorCode.DROP_NOT_ACTIVE);
        }

        // 이미 재고를 선점했거나(RESERVED) 구매까지 완료한(COMPLETED) 유저만 재입장 차단.
        // ENTERED는 재고를 붙잡지 않는 상태라(대기열 통과 후 상세만 보고 나간 경우 포함),
        // 재진입을 막을 이유가 없어 blockStatus에서 제외한다.
        List<EntryStatus> blockStatuses = List.of(EntryStatus.RESERVED, EntryStatus.COMPLETED);

        if (dropEntryRepository.existsByDropIdAndMemberIdAndEntryStatusIn(dropId, memberId, blockStatuses)) {
            throw new BusinessException(ErrorCode.ALREADY_ENTERED, "이미 참여 중이거나 구매가 완료된 드롭입니다.");
        }

        Long rank = queueManager.enqueue(dropId, memberId);

        return QueueRankResult.of(rank);
    }


    @Transactional
    public ConfirmEntryResult confirmEntry(Long dropId) {
        Long memberId = currentMemberPort.getCurrentMemberId();

        if(!queueManager.isActive(dropId, memberId)){
            throw new BusinessException(ErrorCode.UNAUTHORIZED_QUEUE_ACCESS);
        }

        Drop findDrop = dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));

        DropInventory dropInventory = dropInventoryRepository.findByDropId(dropId);

        DropEntry dropEntry;
        if (dropEntryRepository.existsByDropIdAndMemberId(dropId, memberId)) { // 이미 입장한 적이 있으면
            dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId).orElseThrow();
            dropEntry.changeStatusEnter(); // 입장으로 상태 값 변경 (유니크 제약 조건으로 인해 객체를 찾아서 상태 값만 변경)
        }
        else{
            dropEntry = DropEntry.createInitialEntry(dropId, memberId);
        }
        dropEntryRepository.save(dropEntry);

        // 5. 입장 처리 완료 후 대기열 권한 제거
        queueManager.removeActiveUser(dropId, memberId);

        return ConfirmEntryResult.of(findDrop.getDropProduct(), findDrop.getLimitQuantity(), dropInventory.getRemainQuantity(), findDrop.getPickUpAvailableDate());
    }


    public QueueRankResult getRank(Long dropId){ // DB 조회가 일어나지 않기에 @Transaction X
        Long memberId = currentMemberPort.getCurrentMemberId();
        Long rank = queueManager.getRank(dropId, memberId);
        return QueueRankResult.of(rank);
    }

    @Transactional
    public void failExpiredEntries(Long dropId, Set<Long> memberIds) {
        for (Long memberId : memberIds) {
            dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                    .filter(entry -> entry.getEntryStatus() == EntryStatus.ENTERED)
                    .ifPresent(DropEntry::failEntry);
        }
    }
}
