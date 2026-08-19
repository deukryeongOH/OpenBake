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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 일반 상품 장바구니.
 *
 * 장바구니는 재고를 선점하지 않는다. 담기 시점의 재고 검사는 안내용이고,
 * 실제 방어선은 결제 시점 product 의 조건부 재고 차감이다.
 * 그래서 만료 개념이 없고 담아둔 항목은 사용자가 지울 때까지 남는다.
 *
 * 회원당 장바구니는 하나이며 경로에 cartId 가 없다. 대상은 로그인 회원으로 특정한다.
 * 한 상품은 장바구니에 항상 한 행이고, 같은 상품을 또 담으면 수량을 합친다.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductPort productPort;
    private final SellerPort sellerPort;

    /**
     * 장바구니에 상품 담기.
     *
     * 픽업 날짜는 담을 때 고르지 않아도 된다(주문으로 넘어갈 때 필수).
     * 이미 담은 상품이면 수량을 합치고, 픽업 날짜를 이번에 골랐다면 그 값으로 덮어쓴다.
     *
     * 재고 검사는 요청 수량이 아니라 합산 후 수량으로 한다.
     * 이미 3개 담긴 상품에 2개를 더할 때 봐야 할 값은 5다.
     */
    @Transactional
    public CartItemAddResult addItem(Long memberId, Long productId, int quantity, LocalDate pickUpDate) {
        //상품이 없으면(삭제됐으면) 담을 수 없다.
        ProductInfo product = productPort.findProduct(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.generalType()) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_TYPE);
        }

        //품절은 상품 자체가 팔리는지의 전제라, 수량·재고를 보기 전에 먼저 막는다.
        //재고가 모자란 것(CA009)과는 사유가 다르므로 별도 코드(CA011)로 안내한다.
        if (product.soldOut()) {
            throw new BusinessException(ErrorCode.CART_PRODUCT_SOLD_OUT);
        }

        //픽업 날짜를 골랐다면 상품의 픽업 가능일에 실제로 있는지 확인한다.
        //화면 목록은 서버가 내려주지만 요청 본문은 클라이언트가 만든 값이다(위조/stale 방어).
        validatePickUpDate(product, pickUpDate);

        //장바구니가 없으면 이때 만든다. 빈 장바구니를 미리 만들어두지 않는다.
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseGet(() -> cartRepository.save(Cart.create(memberId)));

        //합산 후 수량으로 재고를 확인한다. 검사가 담기보다 먼저 와야 한다.
        int alreadyInCart = cart.findItem(productId)
                .map(CartItem::getQuantity)
                .orElse(0);
        if (alreadyInCart + quantity > product.remainQuantity()) {
            throw new BusinessException(ErrorCode.CART_INSUFFICIENT_STOCK);
        }

        //상호명을 담을 때도 저장해 둔다. 조회는 최신값을 다시 읽지만,
        //상품이 삭제되면 sellerId 를 알 수 없어 판매자를 조회할 수 없으므로 이 값이 마지막 단서가 된다.
        //담을 때 가격을 함께 저장한다. 조회할 때 지금 가격과 비교해 변동 폭을 보여주기 위한 기준값이다.
        cart.addItem(CartItem.create(
                productId, findBakeryName(product.sellerId()), quantity, pickUpDate, product.price()));

        //새로 담긴 항목은 저장이 확정돼야 cartItemId 와 생성/변경 시각이 채워진다.
        //확정하지 않으면 응답의 cartItemId 가 null 로 나간다.
        Cart saved = cartRepository.save(cart);

        //합산됐을 수도 새로 담겼을 수도 있으므로 최종 상태를 다시 읽어 응답한다.
        CartItem item = saved.findItem(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        return CartItemAddResult.from(saved.getCartId(), item);
    }

    /**
     * 장바구니 조회.
     *
     * 장바구니가 없어도 200 으로 빈 목록을 내려준다. 장바구니 페이지는 비어 있어도 열려야 한다.
     * 가격·재고는 조회 시점에 product 에서 다시 읽는다(스냅샷 아님).
     * 상품이 삭제됐거나, 재고가 모자라거나, 픽업일이 선택되지 않았거나 더 이상 유효하지 않은 항목은
     * orderable=false 로 내려 프론트가 비활성 처리한다.
     */
    @Transactional(readOnly = true)
    public CartDetailResult getCart(Long memberId) {
        Optional<Cart> found = cartRepository.findByMemberId(memberId);
        if (found.isEmpty()) {
            return CartDetailResult.empty();
        }
        Cart cart = found.get();

        LocalDate today = LocalDate.now();
        List<CartDetailResult.Item> items = new ArrayList<>();
        //같은 판매자의 상품을 여러 개 담았을 때 판매자를 중복 조회하지 않기 위한 요청 단위 캐시.
        Map<Long, String> bakeryNames = new HashMap<>();

        for (CartItem item : cart.getItems()) {
            Optional<ProductInfo> product = productPort.findProduct(item.getProductId());

            //상품이 사라진 항목. 값은 담을 때 저장해 둔 것만 남는다.
            if (product.isEmpty()) {
                items.add(CartDetailResult.Item.unavailable(item, CartItemStatus.PRODUCT_DELETED));
                continue;
            }
            ProductInfo info = product.get();

            BigDecimal price = BigDecimal.valueOf(info.price());

            //선택 가능한 픽업 날짜도 조회 시점에 다시 읽는다.
            //판매자가 날짜를 추가·수정했으면 늘어난 목록이 그대로 내려가 바로 고를 수 있다.
            List<LocalDate> pickUpDates = info.pickUpAvailableDates().stream()
                    .filter(d -> !d.isBefore(today))
                    .sorted()
                    .toList();

            CartItemStatus status = resolveStatus(info, item, pickUpDates);

            items.add(new CartDetailResult.Item(
                    item.getCartItemId(),
                    info.productId(),
                    //판매자 묶음의 키. 상호명은 바뀔 수 있고 겹칠 수도 있어 묶음 기준으로 쓰지 않는다.
                    info.sellerId(),
                    info.name(),
                    //상호명도 조회 시점에 다시 읽는다. 판매자가 상호를 바꾸면 그대로 반영되며
                    //바뀌었다는 별도 안내는 하지 않는다.
                    bakeryNames.computeIfAbsent(info.sellerId(), this::findBakeryName),
                    info.imageUrl(),
                    price,
                    //담을 때 가격과 변동 여부. 금액 계산에는 쓰지 않고 화면 안내에만 쓴다.
                    item.getAddedPrice() == null ? null : BigDecimal.valueOf(item.getAddedPrice()),
                    item.isPriceChanged(info.price()),
                    item.getQuantity(),
                    price.multiply(BigDecimal.valueOf(item.getQuantity())),
                    item.getPickUpDate(),
                    pickUpDates,
                    info.remainQuantity(),
                    status == CartItemStatus.ORDERABLE,
                    status
            ));
        }

        //주문 가능한 항목만 합산한다. 비활성 항목은 어차피 결제로 못 넘어간다.
        BigDecimal totalAmount = items.stream()
                .filter(CartDetailResult.Item::orderable)
                .map(CartDetailResult.Item::estimatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartDetailResult(cart.getCartId(), items, totalAmount);
    }

    /**
     * 수량 변경. 바꾸려는 수량이 현재 재고를 넘으면 막는다.
     */
    @Transactional
    public CartItemAddResult updateQuantity(Long memberId, Long cartItemId, int quantity) {
        Cart cart = getCart0(memberId);
        CartItem item = findItem(cart, cartItemId);

        ProductInfo product = productPort.findProduct(item.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        //합산이 아니라 교체이므로 요청 수량을 그대로 재고와 비교한다.
        if (quantity > product.remainQuantity()) {
            throw new BusinessException(ErrorCode.CART_INSUFFICIENT_STOCK);
        }

        item.updateQuantity(quantity);

        //변경 시각(updatedAt)이 반영된 값을 응답하려면 저장을 확정해야 한다.
        cartRepository.save(cart);
        return CartItemAddResult.from(cart.getCartId(), item);
    }

    /**
     * 픽업 날짜 선택·변경. 장바구니 페이지에서 다시 고를 수 있다.
     */
    @Transactional
    public CartItemAddResult updatePickUpDate(Long memberId, Long cartItemId, LocalDate pickUpDate) {
        Cart cart = getCart0(memberId);
        CartItem item = findItem(cart, cartItemId);

        ProductInfo product = productPort.findProduct(item.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        //픽업일이 있는지 없는지는 cart 가 판단하지 않는다.
        //장바구니에는 픽업일이 null 인 채로 담겨 있어도 되고, 필수인지는 주문 단계에서 order 가 본다.
        //여기서는 요청으로 들어온 날짜가 상품의 픽업 가능일인지만 확인한다.
        validatePickUpDate(product, pickUpDate);

        item.updatePickUpDate(pickUpDate);

        //변경 시각(updatedAt)이 반영된 값을 응답하려면 저장을 확정해야 한다.
        cartRepository.save(cart);
        return CartItemAddResult.from(cart.getCartId(), item);
    }

    /**
     * 장바구니에서 항목 하나 빼기. 장바구니 행 자체는 남는다.
     * 재고를 선점하지 않았으므로 복구할 것도 없다.
     */
    @Transactional
    public void removeItem(Long memberId, Long cartItemId) {
        Cart cart = getCart0(memberId);
        cart.removeItem(findItem(cart, cartItemId));
    }

    /**
     * 장바구니 비우기. 항목만 지우고 장바구니 행은 남긴다.
     */
    @Transactional
    public void clearItems(Long memberId) {
        getCart0(memberId).clearItems();
    }

    /**
     * memberId 로 카트 존재 여부만 확인한다.
     */
    @Transactional(readOnly = true)
    public boolean hasCart(Long memberId) {
        return cartRepository.existsByMemberId(memberId);
    }

    //판매자를 못 찾으면 null. 상호명은 표시용이라 조회 자체를 실패시키지 않는다.
    private String findBakeryName(Long sellerId) {
        return sellerPort.findSeller(sellerId)
                .map(SellerInfo::bakeryName)
                .orElse(null);
    }

    private Cart getCart0(Long memberId) {
        return cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));
    }

    private CartItem findItem(Cart cart, Long cartItemId) {
        return cart.getItems().stream()
                .filter(i -> i.getCartItemId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    /**
     * 픽업 날짜 검증. 고르지 않았으면(null) 담기 단계에서는 통과시킨다.
     */
    private void validatePickUpDate(ProductInfo product, LocalDate pickUpDate) {
        if (pickUpDate == null) {
            return;
        }
        if (pickUpDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.CART_PICKUP_DATE_UNAVAILABLE);
        }
        if (!product.pickUpAvailableDates().contains(pickUpDate)) {
            throw new BusinessException(ErrorCode.CART_INVALID_PICKUP_DATE);
        }
    }

    private CartItemStatus resolveStatus(ProductInfo product, CartItem item, List<LocalDate> pickUpDates) {
        if (product.soldOut()) {
            return CartItemStatus.SOLD_OUT;
        }
        if (product.remainQuantity() < item.getQuantity()) {
            return CartItemStatus.INSUFFICIENT_STOCK;
        }
        //픽업일은 담을 때 고르지 않아도 되지만 주문으로는 넘길 수 없다.
        //orderable 은 '지금 주문에 포함해도 통과하는가'를 뜻하므로 미선택도 비활성으로 내린다.
        if (item.getPickUpDate() == null) {
            return CartItemStatus.PICKUP_DATE_UNSELECTED;
        }
        //판매자가 픽업 가능일에서 그 날짜를 지웠거나, 고른 날짜가 지나버린 경우다.
        //선택 가능 목록은 최신으로 내려가므로 사용자는 그중에서 다시 고르면 된다.
        if (!pickUpDates.contains(item.getPickUpDate())) {
            return CartItemStatus.PICKUP_DATE_UNAVAILABLE;
        }
        return CartItemStatus.ORDERABLE;
    }
}
