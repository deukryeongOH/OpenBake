package com.openbake.order.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 정산 구매확정 수신 API 호출.
 * 발신함에 저장된 payload(JSON 문자열)를 그대로 본문으로 보낸다.
 * 실패 시 예외를 던져 릴레이가 재시도/실패 처리하게 한다.
 */
@Component
@RequiredArgsConstructor
public class SettlementClient {

    private static final String PURCHASE_CONFIRMED_PATH = "/internal/v1/settlement-events/purchase-confirmed";

    private final RestClient settlementRestClient;

    public void sendPurchaseConfirmed(String payloadJson) {
        settlementRestClient.post()
                .uri(PURCHASE_CONFIRMED_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payloadJson)
                .retrieve()
                .toBodilessEntity();
    }
}
