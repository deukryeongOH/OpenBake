package com.openbake.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // 공용
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 요청입니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "C002", "처리할 수 없는 상태입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "대상을 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C500", "서버 오류가 발생했습니다."),

    // 결제 — 충전
    INVALID_CHARGE_AMOUNT(HttpStatus.BAD_REQUEST, "P001", "충전 금액이 올바르지 않습니다."),
    CHARGE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "P002", "존재하지 않는 충전 요청입니다."),
    CHARGE_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "P003", "본인의 충전 요청이 아닙니다."),
    CHARGE_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "P004", "충전 금액이 일치하지 않습니다."),
    CHARGE_NOT_APPROVABLE(HttpStatus.CONFLICT, "P005", "승인할 수 없는 충전 상태입니다."),
    CHARGE_EXPIRED(HttpStatus.CONFLICT, "P006", "만료된 충전 요청입니다."),
    PG_APPROVE_FAILED(HttpStatus.BAD_GATEWAY, "P007", "결제 승인에 실패했습니다."),
    PG_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "P008", "결제 결과를 확인 중입니다. 잠시 후 내역을 확인해주세요."),

    // 결제 — 예치금
    INVALID_TRANSACTION_TYPE(HttpStatus.BAD_REQUEST, "P009", "유효하지 않은 거래 유형입니다."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "P010", "예치금 잔액이 부족합니다."),

    // 결제 — 주문 결제
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P011", "존재하지 않는 결제입니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "P012", "처리할 수 없는 결제 상태입니다."),
    DEPOSIT_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "P013", "예치금 계좌를 찾을 수 없습니다."),
    PLATFORM_ACCOUNT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "P014", "PLATFORM 계정이 존재하지 않습니다."),
    // Member Domain
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "ME001", "이미 존재하는 리소스입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "ME002", "유효하지 않은 인증 토큰입니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "ME003", "이메일 또는 비밀번호가 일치하지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ME004", "권한이 없습니다."),

    // Seller Domain - SE
    BUSINESS_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "SE001", "사업자 인증에 실패했습니다."),
    INVALID_SETTLEMENT_ACCOUNT(HttpStatus.BAD_REQUEST, "SE002", "은행 코드 또는 계좌번호 형식이 올바르지 않습니다."),
    ACCOUNT_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "SE003", "인증 코드가 일치하지 않습니다."),
    ACCOUNT_VERIFICATION_EXPIRED(HttpStatus.GONE, "SE004", "인증 유효 시간이 만료되었습니다."),
    DUPLICATE_SELLER_APPLICATION(HttpStatus.CONFLICT, "SE005", "이미 판매자 신청을 완료한 회원입니다."),
    ACCOUNT_NOT_VERIFIED(HttpStatus.CONFLICT, "SE006", "계좌 인증이 완료되지 않았습니다."),
    ADMIN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SE007", "권한이 없습니다."),

    // Drop Domain
    DROP_NOT_FOUND(HttpStatus.NOT_FOUND, "DR001", "존재하지 않는 드롭입니다."),
    INVALID_DROP_TIME(HttpStatus.BAD_REQUEST, "DR002", "드롭 시작 시간 또는 마감 시간이 유효하지 않습니다."),
    INVALID_PICKUP_DATE(HttpStatus.BAD_REQUEST, "DR003", "픽업 가능 날짜는 드롭 마감일 이후여야 합니다."),
    DUPLICATE_DROP_DATE(HttpStatus.CONFLICT, "DR004", "해당 날짜에는 이미 등록된 드롭이 존재합니다."),
    INVALID_QUANTITY_LIMIT(HttpStatus.BAD_REQUEST, "DR005", "1인당 제한 수량은 총 수량보다 클 수 없습니다."),
    INVALID_QUANTITY_LIMIT_PER_PERSON(HttpStatus.BAD_REQUEST, "DR0013", "1인당 제한 수량보다 많이 선택했습니다."),
    ALREADY_ENTERED(HttpStatus.CONFLICT, "DR006", "이미 응모 완료된 드롭입니다."),
    DROP_OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "DR007", "준비된 재고가 모두 소진되었습니다."),
    DROP_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "DR008", "현재 응모 가능한 드롭 기간이 아닙니다."),
    UNAUTHORIZED_QUEUE_ACCESS(HttpStatus.BAD_REQUEST, "DR009", "드롭에 입장 할 수 없습니다. 조금만 더 기다려주세요."),
    PLEASE_WAIT(HttpStatus.BAD_REQUEST, "DR010", "대기열 진행 중... 조금만 더 기다려주세요."),
    NEVER_ENTERED(HttpStatus.NOT_FOUND, "DR011", "드롭에 참여한 기록이 없습니다. 드롭에 참여한 후 다시 시도해주세요."),
    LOCK_ACQUISITION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DR012", "락을 획득하는 과정에서 시스템 오류가 발생했습니다."),
    NOT_ENTERED_STATUS(HttpStatus.BAD_REQUEST, "DR014", "재고를 선점할 수 있는 상태가 아닙니다."),
    INVALID_TOTAL_QUANTITY(HttpStatus.BAD_REQUEST, "DR015", "복구할 재고와 남아있는 재고의 합이 총 발매 수량보다 클 수 없습니다."),
    DROP_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "DR016", "본인이 등록한 드롭이 아닙니다."),
    DROP_NOT_EDITABLE(HttpStatus.CONFLICT, "DR017", "이미 시작되었거나 종료된 드롭은 수정/삭제할 수 없습니다."),
    INVALID_USER_SELECT_QUANTITY(HttpStatus.BAD_REQUEST, "DR018", "사용자가 선택한 수량이 남은 수량보다 많을 수 없습니다."),
    TIMESLOT_NOT_CONTAINS(HttpStatus.BAD_REQUEST, "DR019", "선택한 시간은 타임 슬롯에 없는 시간입니다."),
    NOT_RESERVED_STATUS(HttpStatus.BAD_REQUEST, "DR020", "재고를 선점한 상태가 아닙니다."),
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "DR021", "다른 요청과 동시에 처리되어 실패했습니다. 다시 시도해주세요."),

    // product - PR
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PR001", "존재하지 않는 일반 상품입니다."),
    THIS_IS_NOT_YOURS(HttpStatus.BAD_REQUEST, "PR002", "판매자 본인의 상품이 아닙니다."),
    QUANTITY_CAN_NOT_BE_MINUS(HttpStatus.BAD_REQUEST, "PR003", "선택 수량은 음수가 될 수 없습니다."),
    PRODUCT_INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "PR004", "인벤토리가 없습니다."),
    INVALID_PRODUCT_TYPE(HttpStatus.BAD_REQUEST, "PR005", "상품 타입 불일치"),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "PR006", "상품 이미지를 업로드 해주세요."),

    //cart - CA
    CART_ALREADY_EXISTS(HttpStatus.CONFLICT, "CA001", "이미 장바구니에 담긴 상품이 있습니다."),
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "CA002", "장바구니가 없습니다."),
    CART_EXPIRED(HttpStatus.CONFLICT, "CA003", "장바구니가 만료되었습니다. 다시 담아주세요."),
    CART_INVALID_PICKUP_DATE(HttpStatus.BAD_REQUEST, "CA004", "선택할 수 없는 픽업 날짜입니다."),
    CART_PICKUP_DATE_UNAVAILABLE(HttpStatus.CONFLICT, "CA005", "이미 지난 픽업 날짜입니다."),
    CART_STOCK_NOT_RESERVED(HttpStatus.CONFLICT, "CA006", "재고 선점이 확인되지 않았습니다. 다시 담아주세요."),
    CART_STOCK_QUANTITY_MISMATCH(HttpStatus.CONFLICT, "CA007", "선점한 수량과 요청 수량이 다릅니다. 다시 담아주세요."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CA008", "장바구니에 없는 상품입니다."),
    CART_INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "CA009", "남은 재고보다 많은 수량을 담을 수 없습니다."),
    //장바구니에는 픽업 날짜가 null 인 채로 담길 수 있다. 필수 여부는 주문 단계에서 order 가 본다.
    CART_PICKUP_DATE_NOT_SELECTED(HttpStatus.CONFLICT, "CA010", "픽업 날짜를 선택해야 합니다."),
    CART_PRODUCT_SOLD_OUT(HttpStatus.CONFLICT, "CA011", "품절된 상품입니다."),

    //order - OR
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "OR001", "존재하지 않는 주문입니다."),
    ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "OR002", "취소할 수 없는 주문입니다."),
    ORDER_NOT_CONFIRMABLE(HttpStatus.CONFLICT, "OR003", "구매확정할 수 없는 주문입니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "OR004", "약관에 동의해야 합니다."),
    PICKUP_DATE_NOT_SELECTED(HttpStatus.CONFLICT, "OR005", "픽업 날짜를 선택해야 합니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "OR006", "중복된 요청입니다."),
    DROP_ALREADY_CLOSED(HttpStatus.CONFLICT, "OR007", "판매가 마감된 드롭입니다."),
    INVALID_ORDER_STATE(HttpStatus.BAD_REQUEST, "OR008", "유효하지 않은 주문 상태입니다."),

    // Settlement - ST
    SETTLEMENT_BATCH_ALREADY_COMPLETED(HttpStatus.CONFLICT,"ST001","동일한 정산 기간의 배치가 이미 완료됐습니다."),
    SETTLEMENT_BATCH_ALREADY_RUNNING(HttpStatus.CONFLICT,"ST002", "동일한 정산 기간의 배치가 이미 실행 중입니다."),
    SETTLEMENT_BATCH_RESTART_FAILED(HttpStatus.CONFLICT, "ST003", "정산 배치를 재시작할 수 없습니다."),
    INVALID_SETTLEMENT_BATCH_PARAMETERS(HttpStatus.BAD_REQUEST, "ST004", "정산 배치 파라미터가 올바르지 않습니다."),
    DUPLICATE_PAYOUT_REQUEST(HttpStatus.CONFLICT, "ST005", "이미 처리된 지급 요청입니다."),
    SETTLEMENT_BATCH_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ST500", "월 정산 배치 실행 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
