package com.openbake.drop.application;

import com.openbake.drop.application.dto.ConfirmEntryResult;
import com.openbake.drop.application.dto.QueueRankResult;
import com.openbake.drop.application.queue.QueueManager;
import com.openbake.drop.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DropEnterServiceTest {

    @Mock
    private DropEntryRepository dropEntryRepository;

    @Mock
    private QueueManager queueManager;

    @Mock
    private DropRepository dropRepository;

    @Mock
    private DropInventoryRepository dropInventoryRepository;

    @InjectMocks
    private DropEnterService dropEnterService;

    private final Long dropId = 1L;
    private final Long memberId = 10L;

    private Drop activeDrop;
    private DropInventory dropInventory;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        DropProduct dropProduct = DropProduct.builder()
                .name("두쫀쿠")
                .description("원물 맛이 많이 나요.")
                .imageUrl("image.jpg")
                .price(8000)
                .build();

        activeDrop = Drop.builder()
                .dropStatus(DropStatus.ACTIVE)
                .dropProduct(dropProduct)
                .pickUpAvailableDates(Set.of(now.toLocalDate().plusDays(3)))
                .limitQuantity(5)
                .dropStart(now.plusMinutes(1))
                .dropEnd(now.plusMinutes(30))
                .sellerId(1L)
                .build();

        ReflectionTestUtils.setField(activeDrop, "id", dropId);
        // 진행 시간 검증(isAccessible)을 통과시키기 위해 시작 시각을 현재 시각 이전으로 조정
        ReflectionTestUtils.setField(activeDrop, "dropStart", now.minusMinutes(10));

        dropInventory = DropInventory.builder()
                .dropId(dropId)
                .totalQuantity(100)
                .remainQuantity(97)
                .build();
    }

    @Test
    @DisplayName("오늘 진행하는 드롭 ID 조회 성공")
    void getTodayDropId_Success() {
        // given
        given(dropRepository.findByDropStartBetween(any(), any()))
                .willReturn(Optional.of(activeDrop));

        // when
        Long result = dropEnterService.getTodayDropId();

        // then
        assertThat(result).isEqualTo(dropId);
    }

    @Test
    @DisplayName("대기열 진입 성공 - 대기 순번을 반환한다")
    void enterQueue_Success() {
        // given
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));
        given(dropEntryRepository.existsByDropIdAndMemberIdAndEntryStatusIn(
                eq(dropId), eq(memberId), any(List.class))).willReturn(false);
        given(queueManager.enqueue(dropId, memberId)).willReturn(3L);

        // when
        QueueRankResult result = dropEnterService.enterQueue(dropId, memberId);

        // then
        assertThat(result.rank()).isEqualTo(3L);
        assertThat(result.status()).isEqualTo("WAITING");
        verify(queueManager).enqueue(dropId, memberId);
    }

    @Test
    @DisplayName("대기열 순번 조회 성공")
    void getRank_Success() {
        // given
        given(queueManager.getRank(dropId, memberId)).willReturn(0L);

        // when
        QueueRankResult result = dropEnterService.getRank(dropId, memberId);

        // then
        assertThat(result.rank()).isEqualTo(0L);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("입장 확정 성공 - 대기열 권한을 검증하고 진입 내역을 저장한다")
    void confirmEntry_Success() {
        // given
        given(queueManager.isActive(dropId, memberId)).willReturn(true);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));
        given(dropInventoryRepository.findByDropId(dropId)).willReturn(dropInventory);
        given(dropEntryRepository.save(any(DropEntry.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        ConfirmEntryResult result = dropEnterService.confirmEntry(dropId, memberId);

        // then
        assertThat(result.name()).isEqualTo(activeDrop.getDropProduct().getName());
        assertThat(result.description()).isEqualTo(activeDrop.getDropProduct().getDescription());
        assertThat(result.imageUrl()).isEqualTo(activeDrop.getDropProduct().getImageUrl());
        assertThat(result.price()).isEqualTo(activeDrop.getDropProduct().getPrice());
        assertThat(result.limitQuantity()).isEqualTo(activeDrop.getLimitQuantity());
        assertThat(result.remainQuantity()).isEqualTo(dropInventory.getRemainQuantity());
        assertThat(result.pickupDates()).isEqualTo(activeDrop.getPickUpAvailableDate());

        verify(dropEntryRepository).save(any(DropEntry.class));
        verify(queueManager).removeActiveUser(dropId, memberId);
    }

    @Test
    @DisplayName("만료된 멤버 중 ENTERED 상태인 경우 FAILED로 전환한다")
    void failExpiredEntries_FailsEnteredEntries() {
        // given
        Long otherMemberId = 20L;
        DropEntry enteredEntry = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.ENTERED).build();
        DropEntry otherEnteredEntry = DropEntry.builder()
                .dropId(dropId).memberId(otherMemberId).entryStatus(EntryStatus.ENTERED).build();

        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, otherMemberId))
                .willReturn(Optional.of(otherEnteredEntry));

        // when
        dropEnterService.failExpiredEntries(dropId, Set.of(memberId, otherMemberId));

        // then
        assertThat(enteredEntry.getEntryStatus()).isEqualTo(EntryStatus.FAILED);
        assertThat(otherEnteredEntry.getEntryStatus()).isEqualTo(EntryStatus.FAILED);
    }

    @Test
    @DisplayName("이미 RESERVED/COMPLETED 등으로 넘어간 멤버는 건드리지 않는다")
    void failExpiredEntries_SkipsNonEnteredStatus() {
        // given
        DropEntry reservedEntry = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.RESERVED).build();
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(reservedEntry));

        // when
        dropEnterService.failExpiredEntries(dropId, Set.of(memberId));

        // then
        assertThat(reservedEntry.getEntryStatus()).isEqualTo(EntryStatus.RESERVED);
    }

    @Test
    @DisplayName("confirmEntry를 한 번도 호출하지 않아 DropEntry가 없는 멤버는 예외 없이 건너뛴다")
    void failExpiredEntries_SkipsWhenNoDropEntryExists() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.empty());

        // when & then (예외 없이 통과)
        dropEnterService.failExpiredEntries(dropId, Set.of(memberId));
    }
}