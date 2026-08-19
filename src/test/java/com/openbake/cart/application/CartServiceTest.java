package com.openbake.cart.application;

import com.openbake.cart.application.port.ProductPort;
import com.openbake.cart.application.port.SellerPort;
import com.openbake.cart.application.port.dto.ProductInfo;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long SELLER_ID = 3L;
    private static final LocalDate PICKUP_DATE = LocalDate.now().plusDays(7);

    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductPort productPort;
    @Mock
    private SellerPort sellerPort;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, productPort, sellerPort);
    }

    private ProductInfo product(int remainQuantity) {
        return product(false, remainQuantity);
    }

    //soldOut: product 가 품절로 내린 상태인지. 재고 수량과는 별개다.
    private ProductInfo product(boolean soldOut, int remainQuantity) {
        return new ProductInfo(
                PRODUCT_ID, SELLER_ID, "말차 크루아상", 12000,
                "https://cdn.openbake.com/products/7.jpg", true, soldOut,
                Set.of(PICKUP_DATE), remainQuantity
        );
    }

    //영속화된 장바구니를 흉내낸다. 실제로는 JPA 가 ID 를 채운다.
    private Cart persistedCart() {
        Cart cart = Cart.create(MEMBER_ID);
        ReflectionTestUtils.setField(cart, "cartId", 31L);
        return cart;
    }

    private void stubSeller() {
        lenient().when(sellerPort.findSeller(SELLER_ID))
                .thenReturn(Optional.of(new SellerInfo(SELLER_ID, "오픈베이크 베이커리")));
    }

    //담기는 저장을 확정해 cartItemId 를 채운다. 저장한 장바구니를 그대로 돌려주게 둔다.
    private void stubSave() {
        lenient().when(cartRepository.save(org.mockito.ArgumentMatchers.any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------- 담기 ----------

    @Test
    @DisplayName("장바구니가 없으면 만들면서 상품을 담는다")
    void addItem_createsCartWhenAbsent() {
        // given
        Cart created = persistedCart();
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(org.mockito.ArgumentMatchers.any(Cart.class))).thenReturn(created);
        stubSeller();

        // when
        CartItemAddResult result = cartService.addItem(MEMBER_ID, PRODUCT_ID, 2, PICKUP_DATE);

        // then
        assertThat(result.productId()).isEqualTo(PRODUCT_ID);
        assertThat(result.quantity()).isEqualTo(2);
        assertThat(result.pickUpDate()).isEqualTo(PICKUP_DATE);
        assertThat(created.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("픽업 날짜를 고르지 않아도 담을 수 있다")
    void addItem_allowsNullPickUpDate() {
        // given
        Cart cart = persistedCart();
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        stubSeller();
        stubSave();

        // when
        CartItemAddResult result = cartService.addItem(MEMBER_ID, PRODUCT_ID, 1, null);

        // then
        assertThat(result.pickUpDate()).isNull();
        assertThat(cart.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("이미 담긴 상품을 또 담으면 행이 늘지 않고 수량이 합산된다")
    void addItem_mergesQuantity() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 3, PICKUP_DATE, 12000));

        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        stubSeller();
        stubSave();

        // when
        CartItemAddResult result = cartService.addItem(MEMBER_ID, PRODUCT_ID, 2, PICKUP_DATE);

        // then
        assertThat(cart.getItems()).hasSize(1);
        assertThat(result.quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("합산 후 수량이 재고를 넘으면 담기지 않는다")
    void addItem_rejectsWhenMergedQuantityExceedsStock() {
        // given — 재고 4, 이미 3개 담김. 요청 2개만 보면 통과하지만 합산 5개는 초과다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 3, PICKUP_DATE, 12000));

        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(4)));
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));

        // when & then
        assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, PRODUCT_ID, 2, PICKUP_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_INSUFFICIENT_STOCK);

        //거부됐으므로 기존 수량이 그대로여야 한다.
        assertThat(cart.getItems().getFirst().getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("삭제된 상품은 담을 수 없다")
    void addItem_rejectsDeletedProduct() {
        // given
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, PRODUCT_ID, 1, PICKUP_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("판매자가 품절로 내린 상품은 재고가 남아 있어도 담을 수 없다")
    void addItem_rejectsSoldOutStatus() {
        // given — 품절 상태지만 재고 숫자는 남아있다. 재고가 아니라 상태로 막혀야 한다.
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(true, 10)));

        // when & then
        assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, PRODUCT_ID, 1, PICKUP_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_PRODUCT_SOLD_OUT);
    }

    @Test
    @DisplayName("상품의 픽업 가능일이 아니면 담을 수 없다")
    void addItem_rejectsInvalidPickUpDate() {
        // given
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when & then
        assertThatThrownBy(() ->
                cartService.addItem(MEMBER_ID, PRODUCT_ID, 1, PICKUP_DATE.plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_INVALID_PICKUP_DATE);
    }

    @Test
    @DisplayName("이미 지난 픽업 날짜로는 담을 수 없다")
    void addItem_rejectsPastPickUpDate() {
        // given
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when & then — 상품의 픽업 가능일인지 보기 전에 과거인지부터 막는다.
        assertThatThrownBy(() ->
                cartService.addItem(MEMBER_ID, PRODUCT_ID, 1, LocalDate.now().minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_PICKUP_DATE_UNAVAILABLE);
    }

    @Test
    @DisplayName("다시 담을 때 픽업일을 고르지 않으면 기존 픽업일이 지워지지 않는다")
    void addItem_keepsPickUpDateWhenNotChosenOnMerge() {
        // given — 이미 픽업일을 골라 담아둔 상품
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));

        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        stubSeller();
        stubSave();

        // when — 이번에는 수량만 고르고 픽업일은 비워서 담는다.
        CartItemAddResult result = cartService.addItem(MEMBER_ID, PRODUCT_ID, 1, null);

        // then
        assertThat(result.quantity()).isEqualTo(2);
        assertThat(result.pickUpDate()).isEqualTo(PICKUP_DATE);
    }

    @Test
    @DisplayName("합산 후 수량이 재고와 정확히 같으면 담긴다")
    void addItem_allowsMergedQuantityEqualToStock() {
        // given — 재고 5, 이미 3개 담김. 2개를 더하면 딱 5다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 3, PICKUP_DATE, 12000));

        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(5)));
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        stubSeller();
        stubSave();

        // when
        CartItemAddResult result = cartService.addItem(MEMBER_ID, PRODUCT_ID, 2, PICKUP_DATE);

        // then
        assertThat(result.quantity()).isEqualTo(5);
    }

    // ---------- 조회 ----------

    @Test
    @DisplayName("장바구니를 만든 적이 없어도 빈 목록으로 조회된다")
    void getCart_returnsEmptyWhenAbsent() {
        // given
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        assertThat(result.cartId()).isNull();
        assertThat(result.items()).isEmpty();
        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("가격은 스냅샷이 아니라 조회 시점 최신값으로 계산한다")
    void getCart_usesLatestPrice() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 3, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        CartDetailResult.Item item = result.items().getFirst();
        assertThat(item.price()).isEqualByComparingTo(BigDecimal.valueOf(12000));
        assertThat(item.estimatedAmount()).isEqualByComparingTo(BigDecimal.valueOf(36000));
        assertThat(item.orderable()).isTrue();
        assertThat(item.status()).isEqualTo(CartItemStatus.ORDERABLE);
        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(36000));
    }

    @Test
    @DisplayName("담을 때보다 가격이 오르면 담을 때 가격과 함께 변동으로 표시한다")
    void getCart_marksPriceIncrease() {
        // given — 11000원에 담았는데 지금은 12000원이다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 2, PICKUP_DATE, 11000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        CartDetailResult.Item item = result.items().getFirst();
        assertThat(item.priceChanged()).isTrue();
        assertThat(item.addedPrice()).isEqualByComparingTo(BigDecimal.valueOf(11000));
        assertThat(item.price()).isEqualByComparingTo(BigDecimal.valueOf(12000));
        //금액은 언제나 최신 가격 기준이다.
        assertThat(item.estimatedAmount()).isEqualByComparingTo(BigDecimal.valueOf(24000));
    }

    @Test
    @DisplayName("가격이 내려도 변동으로 표시한다")
    void getCart_marksPriceDecrease() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 15000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        assertThat(result.items().getFirst().priceChanged()).isTrue();
    }

    @Test
    @DisplayName("가격이 그대로면 변동으로 표시하지 않는다")
    void getCart_noPriceChange() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        assertThat(result.items().getFirst().priceChanged()).isFalse();
    }

    @Test
    @DisplayName("기준 가격이 없는(예전에 담긴) 항목은 변동 없음으로 본다")
    void getCart_treatsNullAddedPriceAsUnchanged() {
        // given — 컬럼이 생기기 전에 담긴 행
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, null));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        CartDetailResult.Item item = result.items().getFirst();
        assertThat(item.priceChanged()).isFalse();
        assertThat(item.addedPrice()).isNull();
    }

    @Test
    @DisplayName("같은 상품을 다시 담으면 비교 기준 가격이 이번 가격으로 갱신된다")
    void addItem_refreshesAddedPriceOnMerge() {
        // given — 11000원에 담아둔 뒤 12000원으로 오른 상태에서 다시 담는다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 11000));

        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        stubSeller();
        stubSave();

        // when
        cartService.addItem(MEMBER_ID, PRODUCT_ID, 1, PICKUP_DATE);

        // then — 방금 12000원을 보고 담았으므로 더 이상 변동으로 알리지 않는다.
        assertThat(cart.getItems().getFirst().getAddedPrice()).isEqualTo(12000);
        assertThat(cart.getItems().getFirst().isPriceChanged(12000)).isFalse();
    }

    @Test
    @DisplayName("판매자가 상호를 바꾸면 장바구니에도 최신 상호명이 보인다")
    void getCart_refreshesBakeryName() {
        // given — 담을 때 저장된 이름은 옛 상호다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "옛날 베이커리", 1, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(sellerPort.findSeller(SELLER_ID))
                .thenReturn(Optional.of(new SellerInfo(SELLER_ID, "새이름 베이커리")));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        assertThat(result.items().getFirst().bakeryName()).isEqualTo("새이름 베이커리");
    }

    @Test
    @DisplayName("같은 판매자의 상품이 여러 개여도 판매자는 한 번만 조회한다")
    void getCart_readsSellerOncePerSeller() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));
        cart.addItem(CartItem.create(8L, "오픈베이크 베이커리", 1, PICKUP_DATE, 5000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(productPort.findProduct(8L)).thenReturn(Optional.of(
                new ProductInfo(8L, SELLER_ID, "다른 빵", 5000, "img", true, false, Set.of(PICKUP_DATE), 10)));
        when(sellerPort.findSeller(SELLER_ID))
                .thenReturn(Optional.of(new SellerInfo(SELLER_ID, "오픈베이크 베이커리")));

        // when
        cartService.getCart(MEMBER_ID);

        // then
        org.mockito.Mockito.verify(sellerPort, org.mockito.Mockito.times(1)).findSeller(SELLER_ID);
    }

    @Test
    @DisplayName("상품이 삭제된 항목은 비활성으로 내려주고 합계에서 뺀다")
    void getCart_marksDeletedProductUnavailable() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 2, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.empty());

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        CartDetailResult.Item item = result.items().getFirst();
        assertThat(item.orderable()).isFalse();
        assertThat(item.status()).isEqualTo(CartItemStatus.PRODUCT_DELETED);
        //담을 때 저장해 둔 값은 남아 있어야 화면에 무엇이 빠졌는지 보여줄 수 있다.
        assertThat(item.bakeryName()).isEqualTo("오픈베이크 베이커리");
        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("재고가 담아둔 수량보다 적으면 INSUFFICIENT_STOCK 으로 비활성 처리한다")
    void getCart_marksInsufficientStock() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 5, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(2)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        CartDetailResult.Item item = result.items().getFirst();
        assertThat(item.orderable()).isFalse();
        assertThat(item.status()).isEqualTo(CartItemStatus.INSUFFICIENT_STOCK);
        assertThat(item.remainQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("상품이 품절 상태면 SOLD_OUT 으로 비활성 처리한다")
    void getCart_marksSoldOut() {
        // given — 재고가 0이 되면 product 가 상태를 SOLD_OUT 으로 바꾼다. cart 는 그 값을 그대로 쓴다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(true, 0)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        assertThat(result.items().getFirst().status()).isEqualTo(CartItemStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("상태가 아직 SELLING 이면 재고가 0이어도 SOLD_OUT 이 아니라 INSUFFICIENT_STOCK 이다")
    void getCart_marksInsufficientStockWhenStatusStillSelling() {
        // given — 품절 판정은 product 가 한다. cart 는 재고로 품절을 추론하지 않는다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(false, 0)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        CartDetailResult.Item item = result.items().getFirst();
        assertThat(item.status()).isEqualTo(CartItemStatus.INSUFFICIENT_STOCK);
        assertThat(item.orderable()).isFalse();
    }

    @Test
    @DisplayName("판매자가 고른 픽업일을 지우면 PICKUP_DATE_UNAVAILABLE 로 비활성 처리한다")
    void getCart_marksRemovedPickUpDateUnavailable() {
        // given — 08-20 을 골라 담았는데 판매자가 그 날짜를 픽업 가능일에서 뺐다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));

        LocalDate otherDate = PICKUP_DATE.plusDays(3);
        ProductInfo info = new ProductInfo(
                PRODUCT_ID, SELLER_ID, "말차 크루아상", 12000, "img", true, false, Set.of(otherDate), 10);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(info));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        CartDetailResult.Item item = result.items().getFirst();
        assertThat(item.status()).isEqualTo(CartItemStatus.PICKUP_DATE_UNAVAILABLE);
        assertThat(item.orderable()).isFalse();
        //다시 고를 수 있도록 새 목록은 최신으로 내려가야 한다.
        assertThat(item.pickUpAvailableDates()).containsExactly(otherDate);
    }

    @Test
    @DisplayName("판매자가 픽업 가능일을 추가하면 늘어난 목록이 그대로 내려간다")
    void getCart_reflectsAddedPickUpDates() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));

        LocalDate added = PICKUP_DATE.plusDays(1);
        ProductInfo info = new ProductInfo(
                PRODUCT_ID, SELLER_ID, "말차 크루아상", 12000, "img", true, false,
                Set.of(PICKUP_DATE, added), 10);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(info));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then — 기존 선택은 여전히 유효하므로 주문 가능한 채로 목록만 늘어난다.
        CartDetailResult.Item item = result.items().getFirst();
        assertThat(item.pickUpAvailableDates()).containsExactly(PICKUP_DATE, added);
        assertThat(item.status()).isEqualTo(CartItemStatus.ORDERABLE);
    }

    @Test
    @DisplayName("픽업일을 아직 고르지 않은 항목은 주문 대상이 될 수 없다")
    void getCart_marksNullPickUpDateUnselected() {
        // given — 담을 때는 픽업일이 없어도 되지만 주문으로는 넘길 수 없다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, null, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        CartDetailResult.Item item = result.items().getFirst();
        assertThat(item.status()).isEqualTo(CartItemStatus.PICKUP_DATE_UNSELECTED);
        assertThat(item.orderable()).isFalse();
        //주문할 수 없는 항목이므로 합계에도 들어가지 않는다.
        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("품절이면 픽업일 미선택보다 품절을 사유로 내려준다")
    void getCart_prefersSoldOutOverUnselectedPickUpDate() {
        // given — 사유가 겹칠 때는 사용자가 바로 고칠 수 없는 쪽을 먼저 알려야 한다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, null, 12000));

        ProductInfo soldOut = new ProductInfo(
                PRODUCT_ID, SELLER_ID, "말차 크루아상", 12000, "img", true, true,
                Set.of(PICKUP_DATE), 10
        );
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(soldOut));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        assertThat(result.items().getFirst().status()).isEqualTo(CartItemStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("지난 픽업 가능일은 선택지에서 빼고 오름차순으로 내려준다")
    void getCart_filtersPastPickUpDates() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, null, 12000));

        LocalDate past = LocalDate.now().minusDays(1);
        LocalDate near = LocalDate.now().plusDays(1);
        LocalDate far = LocalDate.now().plusDays(5);
        ProductInfo info = new ProductInfo(
                PRODUCT_ID, SELLER_ID, "말차 크루아상", 12000, "img", true, false,
                Set.of(far, past, near), 10
        );

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(info));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        assertThat(result.items().getFirst().pickUpAvailableDates())
                .containsExactly(near, far);
    }

    @Test
    @DisplayName("판매자별로 묶을 수 있도록 sellerId 를 함께 내려준다")
    void getCart_exposesSellerId() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then — 상호명은 바뀔 수 있어 묶음 기준으로 쓰지 않는다.
        assertThat(result.items().getFirst().sellerId()).isEqualTo(SELLER_ID);
    }

    @Test
    @DisplayName("상품이 삭제된 항목은 판매자를 알 수 없어 sellerId 가 비어 있다")
    void getCart_deletedProductHasNoSellerId() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.empty());

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        assertThat(result.items().getFirst().sellerId()).isNull();
    }

    @Test
    @DisplayName("여러 항목이 섞이면 주문 가능한 것만 합산한다")
    void getCart_sumsOnlyOrderableItems() {
        // given — 주문 가능 1개, 품절 1개
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 2, PICKUP_DATE, 12000));
        cart.addItem(CartItem.create(8L, "오픈베이크 베이커리", 3, PICKUP_DATE, 5000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(productPort.findProduct(8L)).thenReturn(Optional.of(
                new ProductInfo(8L, SELLER_ID, "품절된 빵", 5000, "img", true, false, Set.of(PICKUP_DATE), 0)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then — 12000 * 2 만 더해진다. 품절 항목 15000 원은 빠진다.
        assertThat(result.items()).hasSize(2);
        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(24000));
    }

    @Test
    @DisplayName("판매자를 찾지 못하면 상호명만 비워두고 조회는 성공한다")
    void getCart_nullBakeryNameWhenSellerMissing() {
        // given — 상호명은 표시용이라 조회 자체를 실패시키지 않는다.
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(sellerPort.findSeller(SELLER_ID)).thenReturn(Optional.empty());

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        CartDetailResult.Item item = result.items().getFirst();
        assertThat(item.bakeryName()).isNull();
        assertThat(item.orderable()).isTrue();
    }

    @Test
    @DisplayName("재고가 담긴 수량과 정확히 같으면 주문 가능하다")
    void getCart_orderableWhenStockEqualsQuantity() {
        // given — 재고 3, 담긴 수량 3
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 3, PICKUP_DATE, 12000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(3)));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then
        assertThat(result.items().getFirst().status()).isEqualTo(CartItemStatus.ORDERABLE);
    }

    @Test
    @DisplayName("품절이면서 픽업일도 사라졌으면 품절이 먼저다")
    void getCart_soldOutTakesPrecedenceOverPickUpDate() {
        // given — 품절 + 고른 픽업일도 선택 가능일에서 빠진 상태
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));

        ProductInfo info = new ProductInfo(
                PRODUCT_ID, SELLER_ID, "말차 크루아상", 12000, "img", true, true,
                Set.of(PICKUP_DATE.plusDays(3)), 0);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(info));

        // when
        CartDetailResult result = cartService.getCart(MEMBER_ID);

        // then — 재고가 없으면 픽업일을 다시 골라봐야 소용없으므로 품절이 우선이다.
        assertThat(result.items().getFirst().status()).isEqualTo(CartItemStatus.SOLD_OUT);
    }

    // ---------- 수량 · 픽업일 변경 ----------

    @Test
    @DisplayName("수량 변경은 합산이 아니라 교체다")
    void updateQuantity_replaces() {
        // given
        Cart cart = persistedCart();
        CartItem item = CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 3, PICKUP_DATE, 12000);
        ReflectionTestUtils.setField(item, "cartItemId", 104L);
        cart.addItem(item);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when
        CartItemAddResult result = cartService.updateQuantity(MEMBER_ID, 104L, 5);

        // then
        assertThat(result.quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("재고를 넘는 수량으로는 바꿀 수 없다")
    void updateQuantity_rejectsWhenExceedsStock() {
        // given
        Cart cart = persistedCart();
        CartItem item = CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000);
        ReflectionTestUtils.setField(item, "cartItemId", 104L);
        cart.addItem(item);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(3)));

        // when & then
        assertThatThrownBy(() -> cartService.updateQuantity(MEMBER_ID, 104L, 4))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("장바구니에 없는 항목이면 CA008")
    void updateQuantity_rejectsUnknownItem() {
        // given
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(persistedCart()));

        // when & then
        assertThatThrownBy(() -> cartService.updateQuantity(MEMBER_ID, 999L, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("픽업 날짜를 항목별로 다시 고를 수 있다")
    void updatePickUpDate_overwrites() {
        // given
        Cart cart = persistedCart();
        CartItem item = CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, null, 12000);
        ReflectionTestUtils.setField(item, "cartItemId", 104L);
        cart.addItem(item);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when
        CartItemAddResult result = cartService.updatePickUpDate(MEMBER_ID, 104L, PICKUP_DATE);

        // then
        assertThat(result.pickUpDate()).isEqualTo(PICKUP_DATE);
    }

    @Test
    @DisplayName("재고와 정확히 같은 수량으로는 바꿀 수 있다")
    void updateQuantity_allowsQuantityEqualToStock() {
        // given
        Cart cart = persistedCart();
        CartItem item = CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000);
        ReflectionTestUtils.setField(item, "cartItemId", 104L);
        cart.addItem(item);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(5)));

        // when
        CartItemAddResult result = cartService.updateQuantity(MEMBER_ID, 104L, 5);

        // then
        assertThat(result.quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("장바구니가 없는데 수량을 바꾸려 하면 CA002")
    void updateQuantity_rejectsWhenCartAbsent() {
        // given
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.updateQuantity(MEMBER_ID, 104L, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_NOT_FOUND);
    }

    @Test
    @DisplayName("상품이 삭제된 항목은 수량을 바꿀 수 없다")
    void updateQuantity_rejectsDeletedProduct() {
        // given — 재고를 알 수 없으니 검증 자체가 불가능하다.
        Cart cart = persistedCart();
        CartItem item = CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000);
        ReflectionTestUtils.setField(item, "cartItemId", 104L);
        cart.addItem(item);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.updateQuantity(MEMBER_ID, 104L, 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("상품의 픽업 가능일이 아닌 날짜로는 바꿀 수 없다")
    void updatePickUpDate_rejectsInvalidDate() {
        // given — 화면 목록은 서버가 주지만 요청 본문은 클라이언트가 만든 값이다.
        Cart cart = persistedCart();
        CartItem item = CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000);
        ReflectionTestUtils.setField(item, "cartItemId", 104L);
        cart.addItem(item);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when & then
        assertThatThrownBy(() ->
                cartService.updatePickUpDate(MEMBER_ID, 104L, PICKUP_DATE.plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_INVALID_PICKUP_DATE);
    }

    @Test
    @DisplayName("이미 지난 날짜로는 픽업일을 바꿀 수 없다")
    void updatePickUpDate_rejectsPastDate() {
        // given
        Cart cart = persistedCart();
        CartItem item = CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000);
        ReflectionTestUtils.setField(item, "cartItemId", 104L);
        cart.addItem(item);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));

        // when & then
        assertThatThrownBy(() ->
                cartService.updatePickUpDate(MEMBER_ID, 104L, LocalDate.now().minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_PICKUP_DATE_UNAVAILABLE);
    }

    @Test
    @DisplayName("내 장바구니에 없는 항목의 픽업일은 바꿀 수 없다")
    void updatePickUpDate_rejectsUnknownItem() {
        // given — 남의 cartItemId 를 넣어도 여기서 걸린다.
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(persistedCart()));

        // when & then
        assertThatThrownBy(() -> cartService.updatePickUpDate(MEMBER_ID, 999L, PICKUP_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("장바구니가 없는데 픽업일을 바꾸려 하면 CA002")
    void updatePickUpDate_rejectsWhenCartAbsent() {
        // given
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.updatePickUpDate(MEMBER_ID, 104L, PICKUP_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_NOT_FOUND);
    }

    // ---------- 삭제 ----------

    @Test
    @DisplayName("항목을 지워도 장바구니 행은 남는다")
    void removeItem_keepsCart() {
        // given
        Cart cart = persistedCart();
        CartItem item = CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000);
        ReflectionTestUtils.setField(item, "cartItemId", 104L);
        cart.addItem(item);

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));

        // when
        cartService.removeItem(MEMBER_ID, 104L);

        // then
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("비우기는 항목만 지우고 장바구니는 남긴다")
    void clearItems_keepsCart() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(PRODUCT_ID, "오픈베이크 베이커리", 1, PICKUP_DATE, 12000));
        cart.addItem(CartItem.create(8L, "다른 베이커리", 2, PICKUP_DATE, 5000));

        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));

        // when
        cartService.clearItems(MEMBER_ID);

        // then
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getCartId()).isEqualTo(31L);
    }

    @Test
    @DisplayName("장바구니가 없는데 항목을 지우려 하면 CA002")
    void removeItem_rejectsWhenCartAbsent() {
        // given
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.removeItem(MEMBER_ID, 104L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_NOT_FOUND);
    }

    @Test
    @DisplayName("내 장바구니에 없는 항목은 지울 수 없다")
    void removeItem_rejectsUnknownItem() {
        // given — 남의 cartItemId 를 넣어도 내 장바구니 안에서만 찾으므로 못 지운다.
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(persistedCart()));

        // when & then
        assertThatThrownBy(() -> cartService.removeItem(MEMBER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("장바구니가 없는데 비우려 하면 CA002")
    void clearItems_rejectsWhenCartAbsent() {
        // given
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.clearItems(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_NOT_FOUND);
    }

    @Test
    @DisplayName("한 장바구니에 여러 상품을 담을 수 있다")
    void addItem_allowsMultipleProducts() {
        // given
        Cart cart = persistedCart();
        cart.addItem(CartItem.create(8L, "다른 베이커리", 1, PICKUP_DATE, 5000));

        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(cartRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));
        stubSeller();
        stubSave();

        // when
        cartService.addItem(MEMBER_ID, PRODUCT_ID, 1, PICKUP_DATE);

        // then
        assertThat(cart.getItems()).hasSize(2);
        assertThat(cart.getItems())
                .extracting(CartItem::getProductId)
                .containsExactlyInAnyOrder(8L, PRODUCT_ID);
    }
}
