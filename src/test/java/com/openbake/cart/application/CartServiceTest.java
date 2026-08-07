package com.openbake.cart.application;

import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartItem;
import com.openbake.cart.domain.CartRepository;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.DropLockService;
import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropEntry;
import com.openbake.drop.domain.DropEntryRepository;
import com.openbake.drop.domain.DropProduct;
import com.openbake.drop.domain.DropRepository;
import com.openbake.drop.domain.DropStatus;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private DropRepository dropRepository;
    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private DropEntryRepository dropEntryRepository;
    @Mock
    private DropLockService dropLockService;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(
                cartRepository,
                dropRepository,
                sellerRepository,
                dropEntryRepository,
                dropLockService
        );
        //재고 선점 여부 확인용(담기 시 drop 이 만든 응모 상태를 조회).
        ReflectionTestUtils.setField(cartService, "cartTtl", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("재고 선점이 확인되면 장바구니를 생성한다")
    void create_success() {
        // given
        Long memberId = 1L;
        Long dropId = 7L;
        int quantity = 2;

        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.empty());

        DropEntry entry = DropEntry.createInitialEntry(dropId, memberId);
        entry.reserveEntry();
        when(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .thenReturn(Optional.of(entry));

        when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CartCreateResult result = cartService.create(memberId, dropId, quantity);

        // then
        assertThat(result.dropId()).isEqualTo(dropId);
        assertThat(result.quantity()).isEqualTo(quantity);
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("만료되지 않은 장바구니가 있으면 예외가 발생한다")
    void create_alreadyExists() {
        // given
        Long memberId = 1L;
        Cart existing = Cart.create(memberId, LocalDateTime.now().plusMinutes(10));
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.of(existing));

        // when & then
        assertThatThrownBy(() -> cartService.create(memberId, 7L, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_ALREADY_EXISTS);

        verifyNoInteractions(dropEntryRepository);
    }

    @Test
    @DisplayName("만료된 장바구니가 있으면 재고를 복구하고 새로 생성한다")
    void create_expiredCart_rollsBackStock() {
        // given
        Long memberId = 1L;
        Long oldDropId = 5L;
        Cart existing = Cart.create(memberId, LocalDateTime.now().minusMinutes(1));
        existing.addItem(CartItem.create(oldDropId, 3));
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.of(existing));

        Long newDropId = 7L;
        DropEntry entry = DropEntry.createInitialEntry(newDropId, memberId);
        entry.reserveEntry();
        when(dropEntryRepository.findByDropIdAndMemberId(newDropId, memberId))
                .thenReturn(Optional.of(entry));
        when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CartCreateResult result = cartService.create(memberId, newDropId, 2);

        // then
        verify(dropLockService).rollbackStock(oldDropId, memberId, 3);
        verify(cartRepository).deleteImmediately(existing);
        assertThat(result.dropId()).isEqualTo(newDropId);
    }

    @Test
    @DisplayName("재고 선점 응모가 없으면 예외가 발생한다")
    void create_stockNotReserved_noEntry() {
        // given
        Long memberId = 1L;
        Long dropId = 7L;
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.empty());
        when(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.create(memberId, dropId, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_STOCK_NOT_RESERVED);
    }

    @Test
    @DisplayName("응모 상태가 RESERVED가 아니면 예외가 발생한다")
    void create_stockNotReserved_wrongStatus() {
        // given
        Long memberId = 1L;
        Long dropId = 7L;
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.empty());

        DropEntry entry = DropEntry.createInitialEntry(dropId, memberId); // ENTERED, RESERVED 아님
        when(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .thenReturn(Optional.of(entry));

        // when & then
        assertThatThrownBy(() -> cartService.create(memberId, dropId, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_STOCK_NOT_RESERVED);
    }

    @Test
    @DisplayName("장바구니 상세 정보를 조회한다")
    void getCart_success() {
        // given
        Long memberId = 1L;
        Long dropId = 7L;
        Cart cart = Cart.create(memberId, LocalDateTime.now().plusMinutes(15));
        cart.addItem(CartItem.create(dropId, 2));
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.of(cart));

        LocalDate pickupDate = LocalDate.now().plusDays(3);
        Drop drop = createDrop(dropId, 3L, "말차 크루아상", 12000, Set.of(pickupDate));
        when(dropRepository.findById(dropId)).thenReturn(Optional.of(drop));

        Seller seller = createSeller("오픈베이크 연남");
        when(sellerRepository.findById(3L)).thenReturn(Optional.of(seller));

        // when
        CartDetailResult result = cartService.getCart(memberId);

        // then
        assertThat(result.drop().dropId()).isEqualTo(dropId);
        assertThat(result.drop().dropName()).isEqualTo("말차 크루아상");
        assertThat(result.seller().sellerName()).isEqualTo("오픈베이크 연남");
        assertThat(result.quantity()).isEqualTo(2);
        assertThat(result.estimatedAmount()).isEqualByComparingTo("24000");
        assertThat(result.pickupDates()).containsExactly(pickupDate);
    }

    @Test
    @DisplayName("장바구니가 없으면 예외가 발생한다")
    void getCart_notFound() {
        // given
        when(cartRepository.findByMemberId(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.getCart(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_NOT_FOUND);
    }

    @Test
    @DisplayName("만료된 장바구니를 조회하면 예외가 발생한다")
    void getCart_expired() {
        // given
        Cart cart = Cart.create(1L, LocalDateTime.now().minusMinutes(1));
        when(cartRepository.findByMemberId(1L)).thenReturn(Optional.of(cart));

        // when & then
        assertThatThrownBy(() -> cartService.getCart(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_EXPIRED);
    }

    @Test
    @DisplayName("픽업 가능일 안에서 픽업 날짜를 선택한다")
    void updatePickupDate_success() {
        // given
        Long memberId = 1L;
        Long dropId = 7L;
        Cart cart = Cart.create(memberId, LocalDateTime.now().plusMinutes(15));
        cart.addItem(CartItem.create(dropId, 2));
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.of(cart));

        LocalDate pickupDate = LocalDate.now().plusDays(3);
        Drop drop = createDrop(dropId, 3L, "말차 크루아상", 12000, Set.of(pickupDate));
        when(dropRepository.findById(dropId)).thenReturn(Optional.of(drop));

        // when
        CartPickupDateResult result = cartService.updatePickupDate(memberId, pickupDate);

        // then
        assertThat(result.pickupDate()).isEqualTo(pickupDate);
        assertThat(cart.getPickupDate()).isEqualTo(pickupDate);
    }

    @Test
    @DisplayName("이미 지난 날짜를 선택하면 예외가 발생한다")
    void updatePickupDate_pastDate() {
        // given
        Long memberId = 1L;
        Cart cart = Cart.create(memberId, LocalDateTime.now().plusMinutes(15));
        cart.addItem(CartItem.create(7L, 2));
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.of(cart));

        LocalDate pastDate = LocalDate.now().minusDays(1);

        // when & then
        assertThatThrownBy(() -> cartService.updatePickupDate(memberId, pastDate))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_PICKUP_DATE_UNAVAILABLE);

        verifyNoInteractions(dropRepository);
    }

    @Test
    @DisplayName("드롭의 픽업 가능일에 없는 날짜를 선택하면 예외가 발생한다")
    void updatePickupDate_invalidDate() {
        // given
        Long memberId = 1L;
        Long dropId = 7L;
        Cart cart = Cart.create(memberId, LocalDateTime.now().plusMinutes(15));
        cart.addItem(CartItem.create(dropId, 2));
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.of(cart));

        LocalDate availableDate = LocalDate.now().plusDays(3);
        LocalDate requestedDate = LocalDate.now().plusDays(5); // 드롭의 픽업 가능일에 없음
        Drop drop = createDrop(dropId, 3L, "말차 크루아상", 12000, Set.of(availableDate));
        when(dropRepository.findById(dropId)).thenReturn(Optional.of(drop));

        // when & then
        assertThatThrownBy(() -> cartService.updatePickupDate(memberId, requestedDate))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_INVALID_PICKUP_DATE);
    }

    @Test
    @DisplayName("장바구니를 삭제하며 선점 재고를 복구한다")
    void deleteCart_success() {
        // given
        Long memberId = 1L;
        Long dropId = 7L;
        Cart cart = Cart.create(memberId, LocalDateTime.now().plusMinutes(15));
        cart.addItem(CartItem.create(dropId, 2));
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.of(cart));

        // when
        cartService.deleteCart(memberId);

        // then
        verify(dropLockService).rollbackStock(dropId, memberId, 2);
        verify(cartRepository).delete(cart);
    }

    @Test
    @DisplayName("만료된 장바구니를 일괄 정리하고 재고를 복구한다")
    void expireCarts_success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Cart cart1 = Cart.create(1L, now.minusMinutes(1));
        cart1.addItem(CartItem.create(7L, 2));
        Cart cart2 = Cart.create(2L, now.minusMinutes(1));
        cart2.addItem(CartItem.create(8L, 1));

        when(cartRepository.findAllByExpiresAtLessThanEqual(now))
                .thenReturn(List.of(cart1, cart2));

        // when
        int count = cartService.expireCarts(now);

        // then
        assertThat(count).isEqualTo(2);
        verify(dropLockService).rollbackStock(7L, 1L, 2);
        verify(dropLockService).rollbackStock(8L, 2L, 1);
        verify(cartRepository).deleteAll(List.of(cart1, cart2));
    }

    @Test
    @DisplayName("memberId로 장바구니 존재 여부를 확인한다")
    void hasCart() {
        // given
        when(cartRepository.existsByMemberId(1L)).thenReturn(true);

        // when & then
        assertThat(cartService.hasCart(1L)).isTrue();
    }

    private Drop createDrop(Long dropId, Long sellerId, String name, int price, Set<LocalDate> pickupDates) {
        Drop drop = Drop.builder()
                .dropStatus(DropStatus.UPCOMING)
                .dropProduct(DropProduct.builder()
                        .name(name)
                        .description("설명")
                        .imageUrl("https://cdn.openbake.com/drops/" + dropId + ".jpg")
                        .price(price)
                        .build())
                .pickUpAvailableDates(pickupDates)
                .limitQuantity(5)
                .dropStart(LocalDateTime.now().plusHours(1))
                .dropEnd(LocalDateTime.now().plusHours(2))
                .sellerId(sellerId)
                .build();
        ReflectionTestUtils.setField(drop, "id", dropId);
        return drop;
    }

    private Seller createSeller(String bakeryName) {
        return new Seller(
                99L,
                bakeryName,
                "123-45-67890",
                "서울시 마포구",
                "박준영",
                true,
                "088",
                "110-1234-5678",
                "박준영",
                true
        );
    }
}
