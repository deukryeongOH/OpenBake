package com.openbake.cart.application;

import com.openbake.cart.application.port.DropPort;
import com.openbake.cart.application.port.ReservationPort;
import com.openbake.cart.application.port.SellerPort;
import com.openbake.cart.application.port.dto.DropInfo;
import com.openbake.cart.application.port.dto.ReservationInfo;
import com.openbake.cart.application.port.dto.SellerInfo;
import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartItem;
import com.openbake.cart.domain.CartRepository;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private DropPort dropPort;
    @Mock
    private SellerPort sellerPort;
    @Mock
    private ReservationPort reservationPort;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(
                cartRepository,
                dropPort,
                sellerPort,
                reservationPort
        );
        //만료 시간은 설정값이라 테스트에서 직접 주입한다.
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
        when(reservationPort.findReservation(dropId, memberId))
                .thenReturn(Optional.of(new ReservationInfo(true, quantity)));

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

        verifyNoInteractions(reservationPort);
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
        when(reservationPort.findReservation(newDropId, memberId))
                .thenReturn(Optional.of(new ReservationInfo(true, 2)));
        when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CartCreateResult result = cartService.create(memberId, newDropId, 2);

        // then
        verify(reservationPort).rollbackStock(oldDropId, memberId);
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
        when(reservationPort.findReservation(dropId, memberId)).thenReturn(Optional.empty());

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

        //입장은 했지만 선점 전 상태
        when(reservationPort.findReservation(dropId, memberId))
                .thenReturn(Optional.of(new ReservationInfo(false, 0)));

        // when & then
        assertThatThrownBy(() -> cartService.create(memberId, dropId, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_STOCK_NOT_RESERVED);
    }

    @Test
    @DisplayName("요청 수량이 선점 수량보다 많으면 예외가 발생한다")
    void create_quantityMismatch_moreThanReserved() {
        // given
        Long memberId = 1L;
        Long dropId = 7L;
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.empty());

        // drop 이 실제로 선점한 수량은 2
        when(reservationPort.findReservation(dropId, memberId))
                .thenReturn(Optional.of(new ReservationInfo(true, 2)));

        // when & then — 프론트가 선점량보다 많은 3 을 보냈다
        assertThatThrownBy(() -> cartService.create(memberId, dropId, 3))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_STOCK_QUANTITY_MISMATCH);

        //장바구니가 만들어지면 안 된다.
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    @DisplayName("요청 수량이 선점 수량보다 적어도 예외가 발생한다")
    void create_quantityMismatch_lessThanReserved() {
        // given
        Long memberId = 1L;
        Long dropId = 7L;
        when(cartRepository.findByMemberId(memberId)).thenReturn(Optional.empty());

        when(reservationPort.findReservation(dropId, memberId))
                .thenReturn(Optional.of(new ReservationInfo(true, 3)));

        // when & then — 적게 보내도 복구 수량이 어긋나므로 똑같이 막는다
        assertThatThrownBy(() -> cartService.create(memberId, dropId, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_STOCK_QUANTITY_MISMATCH);

        verify(cartRepository, never()).save(any(Cart.class));
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
        when(dropPort.getDrop(dropId))
                .thenReturn(dropInfo(dropId, 3L, "말차 크루아상", 12000, Set.of(pickupDate)));
        when(sellerPort.findSeller(3L))
                .thenReturn(Optional.of(new SellerInfo(3L, "오픈베이크 연남")));

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
        when(dropPort.getDrop(dropId))
                .thenReturn(dropInfo(dropId, 3L, "말차 크루아상", 12000, Set.of(pickupDate)));

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

        verifyNoInteractions(dropPort);
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
        when(dropPort.getDrop(dropId))
                .thenReturn(dropInfo(dropId, 3L, "말차 크루아상", 12000, Set.of(availableDate)));

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
        verify(reservationPort).rollbackStock(dropId, memberId);
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
        verify(reservationPort).rollbackStock(7L, 1L);
        verify(reservationPort).rollbackStock(8L, 2L);
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

    private DropInfo dropInfo(Long dropId, Long sellerId, String name, int price, Set<LocalDate> pickupDates) {
        return new DropInfo(
                dropId,
                sellerId,
                name,
                price,
                "https://cdn.openbake.com/drops/" + dropId + ".jpg",
                pickupDates
        );
    }
}
