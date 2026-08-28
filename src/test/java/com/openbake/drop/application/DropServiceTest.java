package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.drop.application.dto.DropInfoCommand;
import com.openbake.drop.application.dto.DropInfoResult;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.cache.DropCacheInvalidatedEvent;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.application.port.CurrentSellerPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.service.DropService;
import com.openbake.drop.domain.*;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.repository.DropRepository;
import com.openbake.product.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.openbake.drop.domain.DropStatus.UPCOMING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DropServiceTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private TodayDropCache todayDropCache;

    @Mock
    private CurrentSellerPort currentSellerPort;

    @Mock
    private ProductPort productPort;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private DropService dropService;

    private DropInfoCommand command;

    @BeforeEach
    void setUp() {
        Set<LocalDate> pickUpDates = Set.of(
                LocalDate.parse("2028-08-01"),
                LocalDate.parse("2028-08-02"),
                LocalDate.parse("2028-08-03")
        );

        command = DropInfoCommand.create(
                "두쫀쿠",
                "원물 맛이 많이 나요.",
                "C:\\Users\\deukr\\OneDrive\\바탕 화면\\두쫀쿠.jpg",
                LocalDateTime.parse("2028-07-25T13:00:00"),
                LocalDateTime.parse("2028-07-25T14:00:00"),
                200,
                5,
                8000,
                pickUpDates,
                Category.COOKIES_BAKES
        );
    }

    private Drop drop(Long dropId, Long productId, DropStatus status, LocalDateTime start, LocalDateTime end) {
        Drop drop = Drop.builder()
                .dropStatus(status)
                .productId(productId)
                .limitQuantity(3)
                .dropStart(start)
                .dropEnd(end)
                .build();
        ReflectionTestUtils.setField(drop, "id", dropId);
        return drop;
    }

    @Test
    @DisplayName("드롭 등록 성공 - Product가 생성되고 Drop이 저장되며 오늘 드롭 캐시가 갱신되어야 한다")
    void registerDrop_Success() {
        // given
        Long sellerId = 1L;
        Long productId = 100L;

        given(currentSellerPort.getCurrentSellerId()).willReturn(sellerId);
        given(dropRepository.findListByDropDate(any())).willReturn(List.of());

        DropInfoResult productResult = DropInfoResult.of(
                command.dropStart(), command.dropEnd(), command.limitQuantity(), UPCOMING,
                command.name(), command.description(), command.image(), command.pickupDates(),
                command.price(), command.totalQuantity(), command.totalQuantity(), sellerId, productId, null, command.category()
        );
        given(productPort.registerProduct(command)).willReturn(productResult);

        // 저장 시점에 IDENTITY로 채번되는 실제 동작을 흉내낸다 — 실제 JPA는 save() 직후 id를 채워준다.
        Long savedDropId = 999L;
        given(dropRepository.save(any(Drop.class))).willAnswer(invocation -> {
            Drop savedDrop = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedDrop, "id", savedDropId);
            return savedDrop;
        });

        // when
        DropInfoResult response = dropService.registerDrop(command);

        // then
        DropInfoResult expected = DropInfoResult.of(
                productResult.dropStart(), productResult.dropEnd(), productResult.limitQuantity(), productResult.dropStatus(),
                productResult.name(), productResult.description(), productResult.imageUrl(), productResult.pickUpAvailableDates(),
                productResult.price(), productResult.totalQuantity(), productResult.remainQuantity(),
                productResult.sellerId(), productResult.productId(), savedDropId, productResult.category()
        );
        assertThat(response).isEqualTo(expected);
        verify(dropRepository).save(any(Drop.class));
        verify(todayDropCache).refresh();
        verify(applicationEventPublisher).publishEvent(any(DropCacheInvalidatedEvent.class));
    }

    @Test
    @DisplayName("같은 시간대에 이미 등록된 드롭이 있으면 등록에 실패하고, 오늘 드롭 캐시는 갱신하지 않는다")
    void registerDrop_Fail_DuplicateSlot_DoesNotRefreshCache() {
        // given
        Long sellerId = 1L;
        given(currentSellerPort.getCurrentSellerId()).willReturn(sellerId);

        Drop existingDrop = drop(999L, 999L, UPCOMING, command.dropStart(), command.dropEnd());
        given(dropRepository.findListByDropDate(any())).willReturn(List.of(existingDrop));

        // when & then
        assertThatThrownBy(() -> dropService.registerDrop(command))
                .isInstanceOf(BusinessException.class);

        verify(todayDropCache, never()).refresh();
        verifyNoInteractions(productPort);
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    @DisplayName("드롭 수정 성공 - Drop이 갱신되고 오늘 드롭 캐시가 즉시 갱신되어야 한다")
    void updateDropProduct_Success() {
        // given
        Long dropId = 100L;
        Long sellerId = 1L;
        Long productId = 500L;

        Drop existingDrop = drop(dropId, productId, UPCOMING,
                LocalDateTime.parse("2028-07-20T09:00:00"), LocalDateTime.parse("2028-07-20T10:00:00"));

        given(currentSellerPort.getCurrentSellerId()).willReturn(sellerId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(existingDrop));
        given(productPort.getSellerIdByProductId(productId)).willReturn(sellerId);
        given(dropRepository.findListByDropDate(any())).willReturn(List.of());

        DropProductInfoResult productResult = DropProductInfoResult.of(
                command.name(), command.description(), command.image(), command.pickupDates(),
                command.price(), command.totalQuantity(), command.totalQuantity(), sellerId, productId, command.category()
        );
        given(productPort.updateProduct(eq(productId), eq(command))).willReturn(productResult);

        // when
        DropInfoResult response = dropService.updateDropProduct(dropId, command);

        // then
        assertThat(response.name()).isEqualTo(command.name());
        assertThat(response.dropStart()).isEqualTo(command.dropStart());
        assertThat(response.dropEnd()).isEqualTo(command.dropEnd());
        assertThat(response.totalQuantity()).isEqualTo(command.totalQuantity());

        verify(todayDropCache).refresh();
        verify(applicationEventPublisher).publishEvent(any(DropCacheInvalidatedEvent.class));
    }

    @Test
    @DisplayName("본인 소유가 아닌 드롭은 수정할 수 없고, 오늘 드롭 캐시도 갱신하지 않는다")
    void updateDropProduct_Fail_OwnerMismatch_DoesNotRefreshCache() {
        // given
        Long dropId = 100L;
        Long ownerId = 1L;
        Long requesterId = 2L;
        Long productId = 500L;

        Drop existingDrop = drop(dropId, productId, UPCOMING,
                LocalDateTime.parse("2028-07-20T09:00:00"), LocalDateTime.parse("2028-07-20T10:00:00"));

        given(currentSellerPort.getCurrentSellerId()).willReturn(requesterId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(existingDrop));
        given(productPort.getSellerIdByProductId(productId)).willReturn(ownerId);

        // when & then
        assertThatThrownBy(() -> dropService.updateDropProduct(dropId, command))
                .isInstanceOf(BusinessException.class);

        verify(todayDropCache, never()).refresh();
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    @DisplayName("드롭 삭제 성공 - Product와 Drop이 삭제되고 오늘 드롭 캐시가 즉시 갱신되어야 한다")
    void deleteProduct_Success() {
        // given
        Long dropId = 100L;
        Long sellerId = 1L;
        Long productId = 500L;

        Drop existingDrop = drop(dropId, productId, UPCOMING,
                LocalDateTime.parse("2028-07-20T09:00:00"), LocalDateTime.parse("2028-07-20T10:00:00"));

        given(currentSellerPort.getCurrentSellerId()).willReturn(sellerId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(existingDrop));
        given(productPort.getSellerIdByProductId(productId)).willReturn(sellerId);

        // when
        dropService.deleteProduct(dropId);

        // then
        verify(productPort).deleteDropProduct(productId);
        verify(dropRepository).delete(existingDrop);
        verify(todayDropCache).refresh();
        verify(applicationEventPublisher).publishEvent(any(DropCacheInvalidatedEvent.class));
    }

    @Test
    @DisplayName("이미 시작/종료된 드롭은 삭제할 수 없고, 오늘 드롭 캐시도 갱신하지 않는다")
    void deleteProduct_Fail_NotEditable_DoesNotRefreshCache() {
        // given
        Long dropId = 100L;
        Long sellerId = 1L;
        Long productId = 500L;

        Drop existingDrop = drop(dropId, productId, DropStatus.ACTIVE,
                LocalDateTime.parse("2028-07-20T09:00:00"), LocalDateTime.parse("2028-07-20T10:00:00"));

        given(currentSellerPort.getCurrentSellerId()).willReturn(sellerId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(existingDrop));
        given(productPort.getSellerIdByProductId(productId)).willReturn(sellerId);

        // when & then
        assertThatThrownBy(() -> dropService.deleteProduct(dropId))
                .isInstanceOf(BusinessException.class);

        verify(todayDropCache, never()).refresh();
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    @DisplayName("예정된 드롭 목록을 dropStart 오름차순으로 조회한다")
    void getUpcomingDrops_success() {
        // given
        Long productId = 12L;
        Drop drop = drop(productId, productId, UPCOMING,
                LocalDateTime.parse("2028-08-01T13:00:00"), LocalDateTime.parse("2028-08-01T14:00:00"));

        given(dropRepository.findByDropStatusInAndDropStartBetweenOrderByDropStartAsc(
                any(), any(), any()
        )).willReturn(List.of(drop));

        DropProductInfoResult productResult = DropProductInfoResult.of(
                "버터떡", "버터를 많이 써서 향이 좋아요.", "https://cdn.openbake.com/drops/12.jpg",
                Set.of(LocalDate.parse("2028-08-02")), 3000, 200, 200, 1L, productId, Category.COOKIES_BAKES
        );
        given(productPort.getProductInfo(productId)).willReturn(productResult);

        // when
        var response = dropService.getUpcomingDrops(7);

        // then
        assertThat(response).hasSize(1);
        assertThat(response.get(0).productId()).isEqualTo(productId);
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

    @Test
    @DisplayName("일반(GENERAL) 타입 상품은 내 드롭 목록에서 제외된다")
    void getMyDrops_FiltersOutGeneralProducts() {
        // given
        Long sellerId = 1L;
        Long dropProductId = 10L;
        Long generalProductId = 20L;

        given(currentSellerPort.getCurrentSellerId()).willReturn(sellerId);

        DropProductInfoResult dropProduct = DropProductInfoResult.of(
                "두쫀쿠", "d", "i.jpg", Set.of(LocalDate.parse("2028-08-01")), 8000, 100, 100, sellerId, dropProductId, Category.COOKIES_BAKES);
        DropProductInfoResult generalProduct = DropProductInfoResult.of(
                "머핀", "d", "i.jpg", Set.of(LocalDate.parse("2028-08-01")), 3000, 50, 50, sellerId, generalProductId, Category.MEAL_BREADS);

        given(productPort.findProductListBySellerId(sellerId))
                .willReturn(new ArrayList<>(List.of(dropProduct, generalProduct)));
        given(productPort.isGeneralProduct(dropProductId)).willReturn(false);
        given(productPort.isGeneralProduct(generalProductId)).willReturn(true);

        Drop drop = drop(200L, dropProductId, UPCOMING,
                LocalDateTime.parse("2028-08-01T09:00:00"), LocalDateTime.parse("2028-08-01T10:00:00"));
        given(dropRepository.findByProductId(dropProductId)).willReturn(drop);

        // when
        List<DropInfoResult> result = dropService.getMyDrops();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(dropProductId);
        verify(dropRepository, never()).findByProductId(generalProductId);
    }
}