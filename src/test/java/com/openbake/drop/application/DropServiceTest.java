package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.drop.application.queue.TodayDropCache;
import com.openbake.drop.domain.*;
import com.openbake.drop.presentation.dto.DropProductInfoRequest;
import com.openbake.drop.application.dto.DropProductInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.openbake.drop.domain.DropStatus.UPCOMING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;


import static org.mockito.BDDMockito.given;
@ExtendWith(MockitoExtension.class)
class DropServiceTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private DropInventoryRepository dropInventoryRepository; // 추가된 Repository Mock

    @Mock
    private TodayDropCache todayDropCache;

    @InjectMocks
    private DropService dropService;

    private DropProductInfoRequest request;

    @BeforeEach
    void setUp() {
        Set<LocalDate> pickUpDates = Set.of(
                LocalDate.parse("2028-08-01"),
                LocalDate.parse("2028-08-02"),
                LocalDate.parse("2028-08-03")
        );

        request = new DropProductInfoRequest(
                "두쫀쿠",
                "원물 맛이 많이 나요.",
                "C:\\Users\\deukr\\OneDrive\\바탕 화면\\두쫀쿠.jpg",
                pickUpDates,
                LocalDateTime.parse("2028-07-25T13:00:00"),
                LocalDateTime.parse("2028-07-25T14:00:00"),
                5,
                8000,
                200
        );
    }

    @Test
    @DisplayName("드롭 상품 등록 성공 - Drop과 DropInventory가 정상 저장되고 오늘 드롭 캐시가 갱신되어야 한다")
    void registerDropProduct_Success() {
        // given
        Long sellerId = 1L;

        // 1. Mock Drop 엔티티 준비 (DB 저장 후 ID가 할당된 상태 모킹)
        DropProduct dropProduct = DropProduct.builder()
                .name(request.name())
                .description(request.description())
                .imageUrl(request.imageUrl())
                .price(request.price())
                .build();

        Drop savedDrop = Drop.builder()
                .dropStatus(UPCOMING)
                .pickUpAvailableDates(request.pickUpAvailableDates())
                .dropProduct(dropProduct)
                .limitQuantity(request.limitQuantity())
                .dropStart(request.dropStart())
                .dropEnd(request.dropEnd())
                .sellerId(sellerId)
                .build();

        ReflectionTestUtils.setField(savedDrop, "id", 100L);

        // 2. Mock DropInventory 엔티티 준비
        DropInventory savedDropInventory = DropInventory.builder()
                .dropId(100L)
                .totalQuantity(request.totalQuantity())
                .remainQuantity(request.totalQuantity())
                .build();

        // 3. Repository 스터빙 (Stubbing)
        given(dropRepository.save(any())).willReturn(savedDrop);
        given(dropInventoryRepository.save(any(DropInventory.class))).willReturn(savedDropInventory);

        // when
        DropProductInfoResponse response = dropService.registerDropProduct(request, sellerId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo(request.name());
        assertThat(response.description()).isEqualTo(request.description());
        assertThat(response.dropStart()).isEqualTo(request.dropStart());
        assertThat(response.dropEnd()).isEqualTo(request.dropEnd());
        assertThat(response.limitQuantity()).isEqualTo(request.limitQuantity());
        assertThat(response.price()).isEqualTo(request.price());
        assertThat(response.totalQuantity()).isEqualTo(request.totalQuantity());
        assertThat(response.imageUrl()).isEqualTo(request.imageUrl());
        assertThat(response.dropStatus()).isEqualTo(UPCOMING);
        assertThat(response.dropId()).isEqualTo(100L);
        assertThat(response.remainQuantity()).isEqualTo(request.totalQuantity());

        // 4. 실제 DB 저장 메소드가 호출되었는지 검증
        verify(dropRepository).save(any(Drop.class));
        verify(dropInventoryRepository).save(any(DropInventory.class));
        // 5. 자정 캐시가 오늘 새로 등록된 드롭을 놓치지 않도록 즉시 갱신되어야 한다
        verify(todayDropCache).refresh();
    }

    @Test
    @DisplayName("같은 날짜에 이미 등록된 드롭이 있으면 등록에 실패하고, 오늘 드롭 캐시는 갱신하지 않는다")
    void registerDropProduct_Fail_DuplicateDate_DoesNotRefreshCache() {
        // given
        given(dropRepository.existsByDropStartBetween(any(), any())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> dropService.registerDropProduct(request, 1L))
                .isInstanceOf(BusinessException.class);

        verify(todayDropCache, never()).refresh();
    }

    @Test
    @DisplayName("드롭 수정 성공 - Drop/DropInventory가 갱신되고 오늘 드롭 캐시가 즉시 갱신되어야 한다")
    void updateDropProduct_Success() {
        // given
        Long dropId = 100L;
        Long sellerId = 1L;

        DropProduct existingProduct = DropProduct.builder()
                .name("옛날쿠키")
                .description("이전 설명")
                .imageUrl("old.jpg")
                .price(5000)
                .build();

        Drop existingDrop = Drop.builder()
                .dropStatus(UPCOMING)
                .dropProduct(existingProduct)
                .pickUpAvailableDates(Set.of(LocalDate.parse("2028-07-21")))
                .limitQuantity(3)
                .dropStart(LocalDateTime.parse("2028-07-20T10:00:00"))
                .dropEnd(LocalDateTime.parse("2028-07-20T11:00:00"))
                .sellerId(sellerId)
                .build();
        ReflectionTestUtils.setField(existingDrop, "id", dropId);

        DropInventory dropInventory = DropInventory.builder()
                .dropId(dropId)
                .totalQuantity(100)
                .remainQuantity(60)
                .build();

        given(dropRepository.findById(dropId)).willReturn(Optional.of(existingDrop));
        given(dropRepository.existsByDropStartBetweenAndIdNot(any(), any(), eq(dropId))).willReturn(false);
        given(dropRepository.existsBySellerIdAndDropStartBetweenAndIdNot(eq(sellerId), any(), any(), eq(dropId))).willReturn(false);
        given(dropInventoryRepository.findByDropId(dropId)).willReturn(dropInventory);

        // when
        DropProductInfoResponse response = dropService.updateDropProduct(dropId, sellerId, request);

        // then
        assertThat(response.name()).isEqualTo(request.name());
        assertThat(response.dropStart()).isEqualTo(request.dropStart());
        assertThat(response.dropEnd()).isEqualTo(request.dropEnd());
        assertThat(response.totalQuantity()).isEqualTo(request.totalQuantity());
        assertThat(dropInventory.getRemainQuantity()).isEqualTo(request.totalQuantity());

        // 시작/종료 시각이 바뀌었을 수 있으므로 캐시가 즉시 갱신되어야 한다
        verify(todayDropCache).refresh();
    }

    @Test
    @DisplayName("본인 소유가 아닌 드롭은 수정할 수 없고, 오늘 드롭 캐시도 갱신하지 않는다")
    void updateDropProduct_Fail_OwnerMismatch_DoesNotRefreshCache() {
        // given
        Long dropId = 100L;
        Long ownerId = 1L;
        Long requesterId = 2L;

        DropProduct dropProduct = DropProduct.builder()
                .name("두쫀쿠").description("d").imageUrl("i.jpg").price(8000).build();
        Drop drop = Drop.builder()
                .dropStatus(UPCOMING)
                .dropProduct(dropProduct)
                .pickUpAvailableDates(Set.of(LocalDate.parse("2028-07-21")))
                .limitQuantity(3)
                .dropStart(LocalDateTime.parse("2028-07-20T10:00:00"))
                .dropEnd(LocalDateTime.parse("2028-07-20T11:00:00"))
                .sellerId(ownerId)
                .build();
        ReflectionTestUtils.setField(drop, "id", dropId);

        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));

        // when & then
        assertThatThrownBy(() -> dropService.updateDropProduct(dropId, requesterId, request))
                .isInstanceOf(BusinessException.class);

        verify(todayDropCache, never()).refresh();
    }

    @Test
    @DisplayName("드롭 삭제 성공 - Drop/DropInventory가 삭제되고 오늘 드롭 캐시가 즉시 갱신되어야 한다")
    void deleteDropProduct_Success() {
        // given
        Long dropId = 100L;
        Long sellerId = 1L;

        DropProduct dropProduct = DropProduct.builder()
                .name("두쫀쿠").description("d").imageUrl("i.jpg").price(8000).build();
        Drop drop = Drop.builder()
                .dropStatus(UPCOMING)
                .dropProduct(dropProduct)
                .pickUpAvailableDates(Set.of(LocalDate.parse("2028-07-21")))
                .limitQuantity(3)
                .dropStart(LocalDateTime.parse("2028-07-20T10:00:00"))
                .dropEnd(LocalDateTime.parse("2028-07-20T11:00:00"))
                .sellerId(sellerId)
                .build();
        ReflectionTestUtils.setField(drop, "id", dropId);

        DropInventory dropInventory = DropInventory.builder()
                .dropId(dropId)
                .totalQuantity(100)
                .remainQuantity(100)
                .build();

        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));
        given(dropInventoryRepository.findByDropId(dropId)).willReturn(dropInventory);

        // when
        dropService.deleteDropProduct(dropId, sellerId);

        // then
        verify(dropInventoryRepository).delete(dropInventory);
        verify(dropRepository).delete(drop);
        // 삭제된 드롭이 캐시에 남아 스케줄러가 존재하지 않는 드롭을 참조하지 않도록 즉시 갱신되어야 한다
        verify(todayDropCache).refresh();
    }

    @Test
    @DisplayName("이미 시작/종료된 드롭은 삭제할 수 없고, 오늘 드롭 캐시도 갱신하지 않는다")
    void deleteDropProduct_Fail_NotEditable_DoesNotRefreshCache() {
        // given
        Long dropId = 100L;
        Long sellerId = 1L;

        DropProduct dropProduct = DropProduct.builder()
                .name("두쫀쿠").description("d").imageUrl("i.jpg").price(8000).build();
        Drop drop = Drop.builder()
                .dropStatus(DropStatus.ACTIVE)
                .dropProduct(dropProduct)
                .pickUpAvailableDates(Set.of(LocalDate.parse("2028-07-21")))
                .limitQuantity(3)
                .dropStart(LocalDateTime.parse("2028-07-20T10:00:00"))
                .dropEnd(LocalDateTime.parse("2028-07-20T11:00:00"))
                .sellerId(sellerId)
                .build();
        ReflectionTestUtils.setField(drop, "id", dropId);

        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));

        // when & then
        assertThatThrownBy(() -> dropService.deleteDropProduct(dropId, sellerId))
                .isInstanceOf(BusinessException.class);

        verify(todayDropCache, never()).refresh();
    }

    @Test
    @DisplayName("예정된 드롭 목록을 dropStart 오름차순으로 조회한다")
    void getUpcomingDrops_success() {
        // given
        DropProduct dropProduct = DropProduct.builder()
                .name("버터떡")
                .description("버터를 많이 써서 향이 좋아요.")
                .imageUrl("https://cdn.openbake.com/drops/12.jpg")
                .price(3000)
                .build();

        Drop drop = Drop.builder()
                .dropStatus(UPCOMING)
                .pickUpAvailableDates(Set.of(LocalDate.parse("2028-08-02")))
                .dropProduct(dropProduct)
                .limitQuantity(5)
                .dropStart(LocalDateTime.parse("2028-08-01T14:00:00"))
                .dropEnd(LocalDateTime.parse("2028-08-01T18:00:00"))
                .sellerId(1L)
                .build();

        ReflectionTestUtils.setField(drop, "id", 12L);

        DropInventory inventory = DropInventory.builder()
                .dropId(12L)
                .totalQuantity(200)
                .remainQuantity(200)
                .build();

        given(dropRepository.findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
                any(), any(), any()
        )).willReturn(List.of(drop));

        given(dropInventoryRepository.findByDropId(12L))
                .willReturn(inventory);

        // when
        var response = dropService.getUpcomingDrops(7);

        // then
        assertThat(response).hasSize(1);
        assertThat(response.get(0).dropId()).isEqualTo(12L);
        assertThat(response.get(0).name()).isEqualTo("버터떡");
        assertThat(response.get(0).dropStatus()).isEqualTo(UPCOMING);
        assertThat(response.get(0).remainQuantity()).isEqualTo(200);

        verify(dropRepository).findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
                eq(List.of(UPCOMING, DropStatus.ACTIVE)),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("days는 0보다 커야 한다")
    void getUpcomingDrops_invalidDays() {
        assertThatThrownBy(() -> dropService.getUpcomingDrops(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("days는 0보다 커야 합니다.");

        verifyNoInteractions(dropRepository);
    }
}
