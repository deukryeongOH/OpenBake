package com.openbake.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 게이트웨이가 붙인 {@code X-Request-Id}를 MDC에 넣어 로그에 남긴다.
 *
 * <p>이것이 없으면 요청 하나가 서비스를 가로지른 경로를 로그로 따라갈 수 없다.
 * 게이트웨이의 {@code RequestIdFilter}는 헤더를 전달할 뿐이고, 각 서비스가 그 값을
 * 로그에 남겨야 비로소 연결된다.
 *
 * <p>분산 추적(OpenTelemetry)의 대체가 아니다. span별 소요 시간은 알 수 없고,
 * "같은 요청에서 나온 로그"를 묶어주는 것까지가 이 필터의 역할이다.
 *
 * <p>헤더가 없으면 새로 만든다. 게이트웨이를 거치지 않는 내부 호출이나 배치에서도
 * 로그가 서로 섞이지 않게 하려는 것이다.
 */
public class RequestIdMdcFilter extends OncePerRequestFilter implements Ordered {

    public static final String HEADER = "X-Request-Id";

    /** MDC 키. logback 패턴의 %X{requestId}와 짝이다. */
    public static final String MDC_KEY = "requestId";

    /**
     * 게이트웨이의 RequestIdFilter와 같은 형식만 받는다. 외부에서 임의 문자열을
     * 넣어 로그를 오염시키거나 개행으로 로그 위조를 하는 것을 막는다.
     */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = isSafe(incoming) ? incoming : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 톰캣 스레드는 재사용된다. 지우지 않으면 다음 요청 로그에 이전 요청의
            // ID가 남는다.
            MDC.remove(MDC_KEY);
        }
    }

    /** 인증 필터보다 앞에 둔다. 인증 실패 로그에도 ID가 남아야 추적이 끊기지 않는다. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private static boolean isSafe(String requestId) {
        return requestId != null && SAFE_ID.matcher(requestId).matches();
    }
}
