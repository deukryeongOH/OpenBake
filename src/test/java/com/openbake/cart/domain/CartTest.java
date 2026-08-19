package com.openbake.cart.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cart 엔티티 단위 테스트.
 *
 * 서비스를 거치지 않고 "한 상품은 항상 한 행"이라는 규칙 자체를 고정한다.
 */
class CartTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long OTHER_PRODUCT_ID = 8L;
    private static final LocalDate PICKUP_DATE = LocalDate.now().plusDays(7);

    private CartItem item(Long productId, int quantity, LocalDate pickUpDate, Integer addedPrice) {
        return CartItem.create(productId, "오픈베이크 베이커리", quantity, pickUpDate, addedPrice);
    }

    @Test
    @DisplayName("만들면 회원만 정해지고 항목은 비어 있다")
    void create_startsEmpty() {
        // when
        Cart cart = Cart.create(MEMBER_ID);

        // then
        assertThat(cart.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("처음 담는 상품은 새 행이 되고 장바구니와 연결된다")
    void addItem_addsNewRow() {
        // given
        Cart cart = Cart.create(MEMBER_ID);
        CartItem added = item(PRODUCT_ID, 2, PICKUP_DATE, 12000);

        // when
        cart.addItem(added);

        // then — 양방향 연관관계가 채워져야 cart_id 가 저장된다.
        assertThat(cart.getItems()).containsExactly(added);
        assertThat(added.getCart()).isSameAs(cart);
    }

    @Test
    @DisplayName("같은 상품을 또 담으면 행이 늘지 않고 수량이 합쳐진다")
    void addItem_mergesSameProduct() {
        // given
        Cart cart = Cart.create(MEMBER_ID);
        cart.addItem(item(PRODUCT_ID, 2, PICKUP_DATE, 12000));

        // when
        cart.addItem(item(PRODUCT_ID, 3, PICKUP_DATE, 12000));

        // then
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().getFirst().getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("다른 상품은 별도 행으로 담긴다")
    void addItem_keepsDifferentProductsSeparate() {
        // given
        Cart cart = Cart.create(MEMBER_ID);

        // when
        cart.addItem(item(PRODUCT_ID, 1, PICKUP_DATE, 12000));
        cart.addItem(item(OTHER_PRODUCT_ID, 1, PICKUP_DATE, 5000));

        // then
        assertThat(cart.getItems())
                .extracting(CartItem::getProductId)
                .containsExactlyInAnyOrder(PRODUCT_ID, OTHER_PRODUCT_ID);
    }

    @Test
    @DisplayName("담긴 상품은 productId 로 찾을 수 있고 없으면 빈 값이다")
    void findItem_byProductId() {
        // given
        Cart cart = Cart.create(MEMBER_ID);
        cart.addItem(item(PRODUCT_ID, 1, PICKUP_DATE, 12000));

        // when
        Optional<CartItem> found = cart.findItem(PRODUCT_ID);
        Optional<CartItem> missing = cart.findItem(OTHER_PRODUCT_ID);

        // then
        assertThat(found).isPresent();
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("항목을 빼면 목록에서 사라진다")
    void removeItem_removesRow() {
        // given
        Cart cart = Cart.create(MEMBER_ID);
        CartItem added = item(PRODUCT_ID, 1, PICKUP_DATE, 12000);
        cart.addItem(added);

        // when
        cart.removeItem(added);

        // then — orphanRemoval 이 실제 행 삭제를 맡는다.
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("비우면 항목만 사라지고 장바구니는 남는다")
    void clearItems_keepsCart() {
        // given
        Cart cart = Cart.create(MEMBER_ID);
        cart.addItem(item(PRODUCT_ID, 1, PICKUP_DATE, 12000));
        cart.addItem(item(OTHER_PRODUCT_ID, 2, PICKUP_DATE, 5000));

        // when
        cart.clearItems();

        // then
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getMemberId()).isEqualTo(MEMBER_ID);
    }
}
