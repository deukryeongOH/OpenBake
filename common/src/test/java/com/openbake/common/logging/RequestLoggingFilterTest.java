package com.openbake.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 요청 로그가 <b>실제로</b> 나오는지 확인한다.
 *
 * <p>이 필터를 넣은 이유가 "로그가 없어서 추적에서 로그로 넘어갈 수 없다"였다.
 * 그러니 <b>정말 한 줄이 남는지</b>가 검증할 전부다. 설정이 맞는 것과 로그가
 * 나오는 것은 다른 문제다.
 *
 * <p>이 프로젝트에서 같은 부류를 여러 번 겪었다 — 존재하지 않는 지표를 참조한
 * 경보가 조용히 죽었고, 전송 방식이 어긋난 span이 조용히 버려졌다. 공통점은
 * <b>아무것도 오류를 내지 않았다</b>는 점이다.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    @DisplayName("정상 요청이 한 줄로 남는다 — 메서드·경로·상태·소요 시간")
    void logsNormalRequest(CapturedOutput output) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/product-list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(output.getOut())
                .contains("GET")
                .contains("/api/v1/products/product-list")
                .contains("200")
                .contains("ms");
    }

    @Test
    @DisplayName("5xx는 WARN으로 남는다 — 로그 레벨로 걸러 볼 수 있어야 한다")
    void logsServerErrorAsWarn(CapturedOutput output) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(output.getOut()).contains("WARN").contains("500");
    }

    @Test
    @DisplayName("예외가 나도 기록이 남는다 — 그때가 조사에 가장 필요하다")
    void logsEvenWhenChainThrows(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/drops/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain throwing = (req, res) -> {
            throw new IllegalStateException("의도된 예외");
        };

        try {
            filter.doFilter(request, response, throwing);
        } catch (Exception ignored) {
            // 예외는 위로 전파되는 것이 맞다. 여기서 삼키면 오류 처리가 바뀐다.
        }

        assertThat(output.getOut()).contains("/api/v1/drops/1");
    }

    @Test
    @DisplayName("actuator는 남기지 않는다 — 15초마다 오는 헬스체크가 로그를 뒤덮는다")
    void skipsActuator(CapturedOutput output) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/liveness");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // shouldNotFilter를 거치도록 doFilter를 쓴다.
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(output.getOut()).doesNotContain("/actuator/health/liveness");
    }

    @Test
    @DisplayName("MDC보다 뒤에 실행된다 — 그래야 로그에 상관관계 ID가 붙는다")
    void runsAfterMdcFilter() {
        assertThat(filter.getOrder())
                .as("RequestIdMdcFilter가 MDC를 채운 뒤여야 한다")
                .isGreaterThan(new RequestIdMdcFilter().getOrder());
    }
}
