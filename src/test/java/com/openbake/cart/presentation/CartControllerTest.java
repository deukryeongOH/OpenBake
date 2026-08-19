package com.openbake.cart.presentation;

import com.openbake.cart.application.CartDetailResult;
import com.openbake.cart.application.CartItemAddResult;
import com.openbake.cart.application.CartItemStatus;
import com.openbake.cart.application.CartService;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.common.security.CurrentMemberProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CartController 슬라이스 테스트.
 *
 * 서비스 단위 테스트가 못 보는 것을 본다.
 * 요청 검증(@Valid), 응답 상태 코드, 에러 코드 매핑, 그리고
 * 대상 회원을 클라이언트가 정할 수 없다는 점이다.
 *
 * 프로젝트 관례대로 addFilters = false 라 인증 필터는 타지 않는다.
 * 401 자체는 여기서 검증하지 않는다.
 */
@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long SELLER_ID = 3L;
    private static final Long CART_ITEM_ID = 104L;
    private static final LocalDate PICKUP_DATE = LocalDate.now().plusDays(7);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CurrentMemberProvider currentMemberProvider;

    @BeforeEach
    void setUp() {
        when(currentMemberProvider.getId()).thenReturn(MEMBER_ID);
    }

    private CartItemAddResult addResult(int quantity, LocalDate pickUpDate) {
        return new CartItemAddResult(
                31L, CART_ITEM_ID, PRODUCT_ID, quantity, pickUpDate,
                LocalDateTime.of(2026, 8, 13, 14, 0),
                LocalDateTime.of(2026, 8, 13, 14, 20)
        );
    }

    // ---------- 담기 ----------

    @Test
    @DisplayName("담기에 성공하면 201 과 담긴 항목을 돌려준다")
    void addItem_returnsCreated() throws Exception {
        when(cartService.addItem(eq(MEMBER_ID), eq(PRODUCT_ID), eq(2), eq(PICKUP_DATE)))
                .thenReturn(addResult(2, PICKUP_DATE));

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": 7, "quantity": 2, "pickUpDate": "%s"}
                                """.formatted(PICKUP_DATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cartId").value(31))
                .andExpect(jsonPath("$.data.cartItemId").value(104))
                .andExpect(jsonPath("$.data.productId").value(7))
                .andExpect(jsonPath("$.data.quantity").value(2));
    }

    @Test
    @DisplayName("픽업 날짜 없이도 담을 수 있다")
    void addItem_allowsMissingPickUpDate() throws Exception {
        when(cartService.addItem(eq(MEMBER_ID), eq(PRODUCT_ID), eq(1), isNull()))
                .thenReturn(addResult(1, null));

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": 7, "quantity": 1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.pickUpDate").doesNotExist());
    }

    @Test
    @DisplayName("수량이 1 미만이면 400 C001")
    void addItem_rejectsQuantityBelowOne() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": 7, "quantity": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"))
                .andExpect(jsonPath("$.error.message").value("수량은 1개 이상이어야 합니다."));
    }

    @Test
    @DisplayName("상품 ID 가 없으면 400 C001")
    void addItem_rejectsMissingProductId() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("대상 회원은 요청 본문이 아니라 토큰에서 온다")
    void addItem_takesMemberIdFromToken() throws Exception {
        when(cartService.addItem(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(addResult(1, PICKUP_DATE));

        //본문에 memberId 를 끼워 넣어도 무시된다.
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": 7, "quantity": 1, "memberId": 99999}
                                """))
                .andExpect(status().isCreated());

        verify(cartService).addItem(eq(MEMBER_ID), eq(PRODUCT_ID), eq(1), isNull());
    }

    // ---------- 조회 ----------

    @Test
    @DisplayName("장바구니가 없어도 200 과 빈 목록을 돌려준다")
    void getCart_returnsEmpty() throws Exception {
        when(cartService.getCart(MEMBER_ID)).thenReturn(CartDetailResult.empty());

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cartId").doesNotExist())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalAmount").value(0));
    }

    @Test
    @DisplayName("조회 응답에 판매자별로 묶을 sellerId 가 들어 있다")
    void getCart_exposesSellerId() throws Exception {
        CartDetailResult.Item item = new CartDetailResult.Item(
                CART_ITEM_ID, PRODUCT_ID, SELLER_ID, "말차 크루아상", "오픈베이크 베이커리",
                "https://cdn.openbake.com/products/7.jpg",
                BigDecimal.valueOf(12000), BigDecimal.valueOf(11000), true,
                2, BigDecimal.valueOf(24000), PICKUP_DATE, List.of(PICKUP_DATE), 10,
                true, CartItemStatus.ORDERABLE
        );
        when(cartService.getCart(MEMBER_ID))
                .thenReturn(new CartDetailResult(31L, List.of(item), BigDecimal.valueOf(24000)));

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sellerId").value(3))
                .andExpect(jsonPath("$.data.items[0].bakeryName").value("오픈베이크 베이커리"))
                .andExpect(jsonPath("$.data.items[0].priceChanged").value(true))
                .andExpect(jsonPath("$.data.items[0].addedPrice").value(11000))
                .andExpect(jsonPath("$.data.items[0].status").value("ORDERABLE"))
                .andExpect(jsonPath("$.data.totalAmount").value(24000));
    }

    // ---------- 수량 · 픽업일 ----------

    @Test
    @DisplayName("수량을 바꾸면 200 과 바뀐 수량을 돌려준다")
    void updateQuantity_returnsOk() throws Exception {
        when(cartService.updateQuantity(MEMBER_ID, CART_ITEM_ID, 5))
                .thenReturn(addResult(5, PICKUP_DATE));

        mockMvc.perform(patch("/api/v1/cart/items/{cartItemId}/quantity", CART_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(5));
    }

    @Test
    @DisplayName("재고를 넘는 수량이면 409 CA009")
    void updateQuantity_conflictsOnInsufficientStock() throws Exception {
        when(cartService.updateQuantity(MEMBER_ID, CART_ITEM_ID, 99))
                .thenThrow(new BusinessException(ErrorCode.CART_INSUFFICIENT_STOCK));

        mockMvc.perform(patch("/api/v1/cart/items/{cartItemId}/quantity", CART_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 99}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CA009"));
    }

    @Test
    @DisplayName("픽업 날짜를 바꾸면 200 을 돌려준다")
    void updatePickUpDate_returnsOk() throws Exception {
        when(cartService.updatePickUpDate(MEMBER_ID, CART_ITEM_ID, PICKUP_DATE))
                .thenReturn(addResult(1, PICKUP_DATE));

        mockMvc.perform(patch("/api/v1/cart/items/{cartItemId}/pickup-date", CART_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickUpDate": "%s"}
                                """.formatted(PICKUP_DATE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pickUpDate").value(PICKUP_DATE.toString()));
    }

    @Test
    @DisplayName("픽업 날짜가 비어 있으면 400 C001")
    void updatePickUpDate_rejectsMissingDate() throws Exception {
        mockMvc.perform(patch("/api/v1/cart/items/{cartItemId}/pickup-date", CART_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"))
                .andExpect(jsonPath("$.error.message").value("픽업 날짜는 필수입니다."));
    }

    // ---------- 삭제 ----------

    @Test
    @DisplayName("항목을 지우면 204 이고 본문이 없다")
    void removeItem_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/cart/items/{cartItemId}", CART_ITEM_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(cartService).removeItem(MEMBER_ID, CART_ITEM_ID);
    }

    @Test
    @DisplayName("비우면 204 이고 본문이 없다")
    void clearItems_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/cart/items"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(cartService).clearItems(MEMBER_ID);
    }

    // ---------- 남의 항목 접근 ----------

    @Test
    @DisplayName("남의 항목 ID 로 요청해도 404 CA008 로 막힌다")
    void removeItem_rejectsOtherMembersItem() throws Exception {
        //내 장바구니 안에서만 찾으므로 남의 cartItemId 는 애초에 잡히지 않는다.
        doThrow(new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND))
                .when(cartService).removeItem(MEMBER_ID, 999L);

        mockMvc.perform(delete("/api/v1/cart/items/{cartItemId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CA008"));
    }

    @Test
    @DisplayName("경로에 cartId 가 없으므로 대상 장바구니를 바꿔치기할 수 없다")
    void cartIdIsNeverTakenFromRequest() throws Exception {
        when(cartService.getCart(MEMBER_ID)).thenReturn(CartDetailResult.empty());

        //쿼리 파라미터로 남의 cartId 를 넣어도 무시되고 토큰의 회원으로만 조회한다.
        mockMvc.perform(get("/api/v1/cart").param("cartId", "99999"))
                .andExpect(status().isOk());

        verify(cartService).getCart(MEMBER_ID);
    }
}
