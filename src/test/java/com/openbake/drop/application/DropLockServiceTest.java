package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.drop.application.queue.InMemoryQueueManager;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DropLockServiceTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private DropService dropService;

    @Mock
    private DropInventoryRepository dropInventoryRepository;

    @Mock
    private DropEntryRepository dropEntryRepository;

    @Mock
    private InMemoryQueueManager queueManager;

    @InjectMocks
    private DropLockService dropLockService;

    private final Long dropId = 1L;
    private final Long memberId = 10L;

    private DropEntry enteredEntry;
    private DropInventory dropInventory;
    private Drop drop;

    @BeforeEach
    void setUp() {
        enteredEntry = DropEntry.builder()
                .dropId(dropId)
                .memberId(memberId)
                .entryStatus(EntryStatus.ENTERED)
                .build();

        dropInventory = DropInventory.builder()
                .dropId(dropId)
                .totalQuantity(100)
                .remainQuantity(100)
                .build();

        DropProduct dropProduct = DropProduct.builder()
                .name("두쫀쿠")
                .description("원물 맛이 많이 나요.")
                .imageUrl("image.jpg")
                .price(8000)
                .build();

        LocalDateTime now = LocalDateTime.now();
        drop = Drop.builder()
                .dropStatus(DropStatus.ACTIVE)
                .dropProduct(dropProduct)
                .pickUpAvailableDates(Set.of(now.toLocalDate().plusDays(3)))
                .limitQuantity(5)
                .dropStart(now.plusMinutes(1))
                .dropEnd(now.plusMinutes(30))
                .sellerId(1L)
                .build();
    }

    @Test
    @DisplayName("재고 차감 성공 - 재고가 줄고 DropEntry 상태가 RESERVED로 변경된다")
    void decreaseQuantity_Success() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropInventoryRepository.findByDropId(dropId)).willReturn(dropInventory);

        // when
        dropLockService.decreaseQuantity(dropId, memberId, 3);

        // then
        assertThat(dropInventory.getRemainQuantity()).isEqualTo(97);
        assertThat(enteredEntry.getEntryStatus()).isEqualTo(EntryStatus.RESERVED);
    }

    @Test
    @DisplayName("대기열 통과 상태 검증 성공")
    void checkEntryStatus_Success() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));

        // when & then (예외 없이 통과)
        dropLockService.checkEntryStatus(dropId, memberId);
    }

    @Test
    @DisplayName("1인당 제한 수량 검증 성공")
    void checkLimitQuantityPerPerson_Success() {
        // given
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));

        // when & then (예외 없이 통과)
        dropLockService.checkLimitQuantityPerPerson(dropId, 3);
    }

    @Test
    @DisplayName("품절로 COMPLETED된 드롭이 마감 전에 재고가 복구되면 ACTIVE로 되돌리고 soldOut을 해제한다")
    void rollbackStock_Revives_CompletedDropWhenWithinWindowAndStockAvailable() {
        // given
        LocalDateTime now = LocalDateTime.now();
        DropProduct dropProduct = DropProduct.builder()
                .name("두쫀쿠").description("d").imageUrl("i.jpg").price(8000).build();
        Drop soldOutDrop = Drop.builder()
                .dropStatus(DropStatus.ACTIVE) // COMPLETED로 강제 변경 후 사용
                .dropProduct(dropProduct)
                .pickUpAvailableDates(Set.of(now.toLocalDate().plusDays(3)))
                .limitQuantity(5)
                .dropStart(now.plusMinutes(1)) // 빌더는 과거 시작 시각을 허용하지 않으므로 미래로 생성
                .dropEnd(now.plusMinutes(30)) // 아직 마감 전
                .sellerId(1L)
                .build();
        soldOutDrop.changeStatus(DropStatus.COMPLETED); // 품절로 인해 COMPLETED된 상태 재현

        DropInventory soldOutInventory = DropInventory.builder()
                .dropId(dropId)
                .totalQuantity(100)
                .remainQuantity(0) // 품절 상태
                .build();

        given(dropInventoryRepository.findByDropId(dropId)).willReturn(soldOutInventory);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropRepository.findById(dropId)).willReturn(Optional.of(soldOutDrop));

        // when
        dropLockService.rollbackStock(dropId, memberId, 5);

        // then
        assertThat(soldOutInventory.getRemainQuantity()).isEqualTo(5);
        verify(dropService).changeDropStatusActive(dropId);
        verify(queueManager).unmarkSoldOut(dropId);
    }

    @Test
    @DisplayName("마감 시각이 지나 COMPLETED된 드롭은 재고가 복구돼도 ACTIVE로 되돌리지 않는다")
    void rollbackStock_DoesNotRevive_WhenDropWindowAlreadyEnded() {
        // given
        LocalDateTime now = LocalDateTime.now();
        DropProduct dropProduct = DropProduct.builder()
                .name("두쫀쿠").description("d").imageUrl("i.jpg").price(8000).build();
        Drop endedDrop = Drop.builder()
                .dropStatus(DropStatus.ACTIVE)
                .dropProduct(dropProduct)
                .pickUpAvailableDates(Set.of(now.toLocalDate().plusDays(3)))
                .limitQuantity(5)
                .dropStart(now.plusSeconds(1)) // builder 검증(과거 불가) 통과용, 아래서 과거로 되돌림
                .dropEnd(now.plusSeconds(2))
                .sellerId(1L)
                .build();
        ReflectionTestUtils.setField(endedDrop, "dropEnd", now.minusMinutes(1)); // 마감 시각을 과거로 조정
        endedDrop.changeStatus(DropStatus.COMPLETED);

        DropInventory soldOutInventory = DropInventory.builder()
                .dropId(dropId)
                .totalQuantity(100)
                .remainQuantity(0)
                .build();

        given(dropInventoryRepository.findByDropId(dropId)).willReturn(soldOutInventory);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropRepository.findById(dropId)).willReturn(Optional.of(endedDrop));

        // when
        dropLockService.rollbackStock(dropId, memberId, 5);

        // then
        verify(dropService, never()).changeDropStatusActive(any());
        verify(queueManager, never()).unmarkSoldOut(any());
    }

    @Test
    @DisplayName("COMPLETED가 아닌(정상 진행 중인) 드롭은 재고 롤백만 하고 상태 전환은 시도하지 않는다")
    void rollbackStock_DoesNotRevive_WhenDropIsNotCompleted() {
        // given
        DropInventory partiallyReservedInventory = DropInventory.builder()
                .dropId(dropId)
                .totalQuantity(100)
                .remainQuantity(97) // 아직 totalQuantity에 여유가 있는 정상 진행 중 상태
                .build();

        given(dropInventoryRepository.findByDropId(dropId)).willReturn(partiallyReservedInventory);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop)); // status = ACTIVE

        // when
        dropLockService.rollbackStock(dropId, memberId, 3);

        // then
        assertThat(partiallyReservedInventory.getRemainQuantity()).isEqualTo(100);
        verify(dropService, never()).changeDropStatusActive(any());
        verify(queueManager, never()).unmarkSoldOut(any());
    }
}
