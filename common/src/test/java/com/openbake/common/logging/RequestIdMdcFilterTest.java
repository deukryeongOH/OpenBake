package com.openbake.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdMdcFilterTest {

    private final RequestIdMdcFilter filter = new RequestIdMdcFilter();

    /** 필터 안에서의 MDC 값을 확인해야 하므로 체인 안에서 읽어 둔다. */
    private static class Capturing implements FilterChain {
        String seen;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            seen = MDC.get(RequestIdMdcFilter.MDC_KEY);
        }
    }

    @Test
    void 게이트웨이가_보낸_아이디를_그대로_쓴다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdMdcFilter.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Capturing chain = new Capturing();

        filter.doFilter(request, response, chain);

        assertThat(chain.seen).isEqualTo("abc-123");
        assertThat(response.getHeader(RequestIdMdcFilter.HEADER)).isEqualTo("abc-123");
    }

    @Test
    void 헤더가_없으면_새로_만든다() throws Exception {
        Capturing chain = new Capturing();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // 게이트웨이를 거치지 않는 내부 호출·배치에서도 로그가 섞이지 않아야 한다.
        assertThat(chain.seen).isNotBlank();
    }

    @Test
    void 형식에_맞지_않는_값은_버리고_새로_만든다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 개행이 들어가면 로그 한 줄을 위조할 수 있다.
        request.addHeader(RequestIdMdcFilter.HEADER, "bad\nvalue INJECTED");
        Capturing chain = new Capturing();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.seen).doesNotContain("INJECTED").doesNotContain("\n");
    }

    @Test
    void 요청이_끝나면_MDC를_비운다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdMdcFilter.HEADER, "abc-123");

        filter.doFilter(request, new MockHttpServletResponse(), new Capturing());

        // 톰캣 스레드는 재사용된다. 남겨 두면 다음 요청 로그에 이전 ID가 찍힌다.
        assertThat(MDC.get(RequestIdMdcFilter.MDC_KEY)).isNull();
    }
}
