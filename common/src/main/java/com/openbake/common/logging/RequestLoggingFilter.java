package com.openbake.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 하나당 로그 한 줄을 남긴다.
 *
 * <p><b>왜 필요한가.</b> 지금까지 서비스들은 요청 처리 중에 로그를 거의 남기지
 * 않았다. 정상 요청은 물론이고 401 응답조차 조용히 지나갔다. 2026-08-27 실측에서
 * backend가 10분간 요청 수십 건을 처리했는데 로그는 기동 로그 86줄이 전부였다.
 *
 * <p>그래서 <b>"지표 → 추적 → 로그" 흐름의 마지막 단계가 막혀 있었다.</b>
 * 추적에서 느린 요청을 찾아도 그 시각에 해당하는 로그가 없어 원인으로 못 넘어간다.
 * traceId를 MDC에 넣고 구조화 로그까지 갖췄지만, 정작 <b>흘려보낼 로그가 없었다.</b>
 *
 * <p>이 필터가 남기는 한 줄에 traceId가 자동으로 붙는다(구조화 로그가 MDC를
 * 포함한다). 그러면 Grafana에서 trace의 traceId로 로그를 바로 찾을 수 있다.
 *
 * <p><b>무엇을 남기지 않는가.</b> 요청 본문과 응답 본문은 남기지 않는다. 개인정보와
 * 결제 정보가 그대로 들어가고, 양도 감당할 수 없다. 남기는 것은 메서드·경로·상태
 * 코드·소요 시간뿐이다.
 *
 * <p>쿼리 문자열도 뺀다. 검색어나 토큰이 들어올 수 있다.
 */
public class RequestLoggingFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /**
     * 로그를 남기지 않을 경로.
     *
     * <p>헬스체크는 쿠버네티스가 15초마다 찌른다. 서비스 5개면 하루 10만 줄이 넘고,
     * 그 안에서 진짜 요청을 찾는 것이 오히려 어려워진다. 관측 도구가 관측을
     * 방해하는 셈이다.
     */
    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/actuator",
            "/favicon.ico");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            // finally에 두는 이유: 예외가 나도 요청 기록은 남아야 한다.
            // 오히려 그때가 조사에 가장 필요한 로그다.
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            int status = response.getStatus();

            // 5xx는 WARN으로 올린다. 로그 레벨로 걸러 볼 수 있어야 한다.
            if (status >= 500) {
                log.warn("{} {} {} {}ms",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs);
            } else {
                log.info("{} {} {} {}ms",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String prefix : SKIP_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link RequestIdMdcFilter} 바로 뒤에 둔다.
     *
     * <p>그 필터가 MDC에 requestId를 넣은 뒤여야 이 로그에도 값이 붙는다. 앞에 두면
     * 정작 상관관계 ID 없는 로그가 남는다.
     *
     * <p>인증 필터보다는 앞이다. 401·403으로 끝난 요청도 기록되어야 한다.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 21;
    }
}
