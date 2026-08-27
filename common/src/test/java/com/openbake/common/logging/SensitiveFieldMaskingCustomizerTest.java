package com.openbake.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 마스킹 규칙을 검증한다.
 *
 * <p>구조화 로그를 켜면 로그가 Loki로 흘러가 <b>오래 보관되고 검색 가능해진다.</b>
 * 지금까지는 Pod가 죽으면 로그도 사라져 노출 창이 짧았다. 앞으로는 한 번 새어 나간
 * 값이 계속 남으므로, 이 그물에 구멍이 있으면 그대로 사고가 된다.
 *
 * <p>가리지 <b>말아야</b> 할 것도 함께 검증한다. 과하게 가리면 로그가 쓸모없어져
 * 장애 조사가 오히려 느려진다.
 */
class SensitiveFieldMaskingCustomizerTest {

    private static String maskText(String input) throws Exception {
        Method m = SensitiveFieldMaskingCustomizer.class
                .getDeclaredMethod("maskInText", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, input);
    }

    @Nested
    @DisplayName("메시지 본문에 섞여 들어온 값")
    class InText {

        @Test
        @DisplayName("카드번호는 구분자가 있든 없든 가려진다")
        void masksCardNumbers() throws Exception {
            assertThat(maskText("결제 카드 1234-5678-9012-3456 승인")).doesNotContain("5678-9012");
            assertThat(maskText("카드 1234 5678 9012 3456")).doesNotContain("5678 9012");
            assertThat(maskText("카드 1234567890123456")).doesNotContain("1234567890123456");
        }

        @Test
        @DisplayName("주민등록번호가 가려진다")
        void masksResidentRegistrationNumber() throws Exception {
            assertThat(maskText("고객 900101-1234567 확인")).doesNotContain("1234567");
        }

        @Test
        @DisplayName("key=value 형태로 박힌 토큰이 가려진다")
        void masksInlineToken() throws Exception {
            String masked = maskText("인증 실패 token=eyJhbGciOiJIUzI1NiJ9.abc");
            assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
            assertThat(masked).contains("token=***");
        }

        @Test
        @DisplayName("password와 api_key도 같은 방식으로 가려진다")
        void masksOtherInlineSecrets() throws Exception {
            assertThat(maskText("password: hunter2xyz")).doesNotContain("hunter2xyz");
            assertThat(maskText("api_key=AKIAIOSFODNN7EXAMPLE")).doesNotContain("AKIAIOSFODNN7EXAMPLE");
        }
    }

    @Nested
    @DisplayName("가리지 말아야 할 것 — 과잉 마스킹은 조사를 방해한다")
    class ShouldNotMask {

        @Test
        @DisplayName("주문 ID나 재고 수량 같은 평범한 숫자는 남는다")
        void keepsOrdinaryNumbers() throws Exception {
            assertThat(maskText("드롭 재고 확정. dropId=71, remain=89")).contains("71").contains("89");
            assertThat(maskText("주문 12345 처리 완료")).contains("12345");
        }

        @Test
        @DisplayName("요청 ID와 타임스탬프는 남는다 — 이게 없으면 추적이 끊긴다")
        void keepsRequestIdAndTimestamp() throws Exception {
            String id = "90238335-1538-4c95-a9a8-02e381615118";
            assertThat(maskText("요청 시작 " + id)).contains(id);
            assertThat(maskText("2026-08-27T02:13:19Z 처리")).contains("2026-08-27");
        }

        @Test
        @DisplayName("일반 문장은 그대로 통과한다")
        void keepsPlainText() throws Exception {
            String s = "미결 충전 확인 시작";
            assertThat(maskText(s)).isEqualTo(s);
        }
    }

    @Nested
    @DisplayName("키 이름 기준 마스킹")
    class ByKeyName {

        private static boolean sensitive(String key) throws Exception {
            Method m = SensitiveFieldMaskingCustomizer.class
                    .getDeclaredMethod("isSensitiveKey", org.springframework.boot.json.JsonWriter.MemberPath.class);
            m.setAccessible(true);
            return (boolean) m.invoke(null, org.springframework.boot.json.JsonWriter.MemberPath.of(key));
        }

        @Test
        @DisplayName("민감한 키 이름을 부분 일치로 잡는다")
        void detectsSensitiveKeys() throws Exception {
            assertThat(sensitive("password")).isTrue();
            assertThat(sensitive("accessToken")).isTrue();
            assertThat(sensitive("refresh_token")).isTrue();
            assertThat(sensitive("Authorization")).isTrue();
            assertThat(sensitive("cardNumber")).isTrue();
        }

        @Test
        @DisplayName("추적에 필요한 키는 건드리지 않는다")
        void keepsTracingKeys() throws Exception {
            assertThat(sensitive("requestId")).isFalse();
            assertThat(sensitive("traceId")).isFalse();
            assertThat(sensitive("orderId")).isFalse();
            assertThat(sensitive("message")).isFalse();
        }
    }
}
