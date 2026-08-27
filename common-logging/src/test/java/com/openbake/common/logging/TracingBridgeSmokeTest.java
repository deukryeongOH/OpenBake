package com.openbake.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * 분산 추적 브리지가 <b>실제로</b> traceId를 만들고 로그에 넣는지 확인한다.
 *
 * <p>설정이 문법적으로 맞는 것과 그 설정이 동작하는 것은 다르다. 의존성만 넣고
 * 자동 설정이 켜지지 않으면 {@code %X{traceId}} 자리가 조용히 비고, 배포 후에야
 * 안다. 그때는 이미 "추적이 되는 줄 알았던" 기간의 로그가 쓸모없어진 뒤다.
 *
 * <p>이 프로젝트에서 같은 부류의 실패를 여러 번 겪었다. 존재하지 않는 지표 이름을
 * 참조한 경보가 오류 없이 영원히 발화하지 않았고, 클래스패스에 없는 커스터마이저를
 * 설정에 적어 기동이 실패했다. 공통점은 <b>문자열로 적은 것이 실체와 어긋났는데
 * 아무도 오류를 내지 않았다</b>는 점이다.
 *
 * <p>샘플링을 1.0으로 강제한다. 낮추면 span이 만들어지지 않는 실행이 섞여
 * 테스트가 간헐적으로 실패한다.
 */
@SpringBootTest(classes = TracingBridgeSmokeTest.Config.class)
@TestPropertySource(properties = {
        "management.tracing.sampling.probability=1.0",
        "logging.pattern.console=%-5level [%X{traceId:-},%X{spanId:-}] %logger{20} - %msg%n"
})
@ExtendWith(OutputCaptureExtension.class)
class TracingBridgeSmokeTest {

    @SpringBootApplication
    static class Config {
    }

    private static final Logger log = LoggerFactory.getLogger(TracingBridgeSmokeTest.class);

    @Autowired
    private Tracer tracer;

    @Test
    @DisplayName("Tracer 빈이 등록된다 — 자동 설정이 실제로 켜졌는가")
    void tracerBeanExists() {
        assertThat(tracer)
                .as("micrometer-tracing-bridge-otel이 있어도 자동 설정이 안 켜지면 null이다")
                .isNotNull();
    }

    @Test
    @DisplayName("span 안에서 찍은 로그에 traceId가 들어간다")
    void logsCarryTraceId(CapturedOutput output) {
        Span span = tracer.nextSpan().name("smoke");
        String traceId;
        try (Tracer.SpanInScope ignored = tracer.withSpan(span.start())) {
            traceId = span.context().traceId();
            log.info("추적 확인용 메시지");
        } finally {
            span.end();
        }

        assertThat(traceId).as("traceId가 생성되어야 한다").isNotBlank();
        String line = lineContaining(output, "추적 확인용 메시지");
        assertThat(line).as("로그 줄을 찾지 못했다").isNotNull();
        assertThat(line)
                .as("로그 패턴의 %X{traceId} 자리가 채워져야 한다")
                .contains(traceId);
    }

    @Test
    @DisplayName("span 밖에서는 traceId 자리가 비어 있다 — 값이 새지 않는다")
    void logsOutsideSpanHaveNoTraceId(CapturedOutput output) {
        log.info("span 밖 메시지");

        String line = lineContaining(output, "span 밖 메시지");
        assertThat(line).isNotNull();
        // 패턴이 [,] 형태로 남는다. 이전 요청의 traceId가 남아 있으면 안 된다.
        assertThat(line).contains("[,]");
    }

    private static String lineContaining(CapturedOutput output, String needle) {
        String found = null;
        for (String line : output.getOut().split("\\R")) {
            if (line.contains(needle)) {
                found = line;
            }
        }
        return found;
    }
}
