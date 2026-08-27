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
 * <b>운영과 같은 조합</b>에서 traceId가 로그에 실제로 들어가는지 확인한다.
 *
 * <p>부분별로는 이미 검증돼 있다. {@code TracingBridgeSmokeTest}는 추적이 동작함을,
 * {@code StructuredLoggingSmokeTest}는 JSON 로그가 나옴을 확인한다. 그런데
 * <b>둘을 합쳤을 때 traceId가 JSON 필드로 들어가는지는 별개 문제다.</b>
 *
 * <p>구조화 로그가 MDC를 포함하는지는 설정 메타데이터만으로 판단할 수 없었다
 * ({@code logging.structured.json.context.include}의 기본값이 문서화되어 있지 않다).
 * 그래서 추측하지 않고 실제 출력으로 확인한다.
 *
 * <p>이것이 중요한 이유: 운영은 ECS 구조화 로그를 쓴다. 여기에 traceId가 없으면
 * 나중에 Loki에서 trace로 로그를 찾을 수 없고, 추적을 붙인 의미가 절반 사라진다.
 * 배포 후에야 알면 그 사이 쌓인 로그는 되살릴 수 없다.
 */
@SpringBootTest(classes = StructuredLogCarriesTraceIdTest.Config.class)
@TestPropertySource(properties = {
        // 운영과 같은 설정
        "logging.structured.format.console=ecs",
        "logging.structured.ecs.service.name=openbake-test",
        "logging.structured.ecs.service.environment=test",
        "management.tracing.sampling.probability=1.0"
})
@ExtendWith(OutputCaptureExtension.class)
class StructuredLogCarriesTraceIdTest {

    @SpringBootApplication
    static class Config {
    }

    private static final Logger log = LoggerFactory.getLogger(StructuredLogCarriesTraceIdTest.class);

    @Autowired
    private Tracer tracer;

    @Test
    @DisplayName("ECS JSON 로그에 traceId가 필드로 들어간다")
    void ecsLogContainsTraceId(CapturedOutput output) {
        Span span = tracer.nextSpan().name("ecs-trace");
        String traceId;
        try (Tracer.SpanInScope ignored = tracer.withSpan(span.start())) {
            traceId = span.context().traceId();
            log.info("ECS 추적 확인용 메시지");
        } finally {
            span.end();
        }

        String line = lastJsonLine(output, "ECS 추적 확인용 메시지");
        assertThat(line).as("JSON 로그 줄을 찾지 못했다").isNotNull();

        // 이 모듈에는 Jackson이 없으므로 파싱 대신 형태만 확인한다.
        // 서비스 모듈에서는 StructuredLoggingSmokeTest가 파싱까지 검증한다.
        assertThat(line).startsWith("{").endsWith("}");

        assertThat(line)
                .as("운영 로그에 traceId가 없으면 나중에 trace로 로그를 찾을 수 없다."
                        + " 필요하면 logging.structured.json.context.include=true를 명시한다."
                        + " 실제 출력: " + line)
                .contains(traceId);
    }

    private static String lastJsonLine(CapturedOutput output, String needle) {
        String found = null;
        for (String line : output.getOut().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("{") && trimmed.contains(needle)) {
                found = trimmed;
            }
        }
        return found;
    }
}
