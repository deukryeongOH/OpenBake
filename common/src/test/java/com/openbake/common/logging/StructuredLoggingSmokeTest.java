package com.openbake.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 구조화 로깅이 <b>실제로</b> JSON을 뱉는지 확인한다.
 *
 * <p>설정이 문법적으로 맞는 것과 그 설정이 의도대로 동작하는 것은 다른 문제다.
 * {@code logging.structured.json.customizer}에 적은 클래스 이름이 틀리면 기동
 * 자체가 실패하고, 형식 이름이 틀리면 조용히 평문으로 나온다. 배포 후에야 알면
 * 이미 Loki에 파싱 불가능한 로그가 쌓인 뒤다.
 *
 * <p>운영과 같은 설정을 프로퍼티로 직접 주어 검증한다. prod 프로필 파일을 그대로
 * 읽지 않는 이유는 그 파일에 DB 접속 정보 같은 다른 설정이 함께 있어서다.
 */
@SpringBootTest(classes = StructuredLoggingSmokeTest.Config.class)
@TestPropertySource(properties = {
        "logging.structured.format.console=ecs",
        "logging.structured.ecs.service.name=openbake-test",
        "logging.structured.ecs.service.environment=test",
        "logging.structured.json.customizer=com.openbake.common.logging.SensitiveFieldMaskingCustomizer"
})
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingSmokeTest {

    @org.springframework.boot.autoconfigure.SpringBootApplication
    static class Config {
    }

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingSmokeTest.class);

    @Test
    @DisplayName("로그가 JSON 한 줄로 나오고 service 이름이 들어간다")
    void emitsJsonWithServiceName(CapturedOutput output) throws Exception {
        log.info("구조화 로깅 확인용 메시지");

        String line = lastJsonLine(output, "구조화 로깅 확인용 메시지");
        assertThat(line).as("JSON 로그 줄을 찾지 못했다").isNotNull();

        // 파싱까지 해서 진짜 JSON인지 확인한다. 문자열 포함 검사만으로는
        // 깨진 JSON도 통과한다.
        Map<String, Object> parsed = new org.springframework.boot.json.JacksonJsonParser()
                .parseMap(line);
        assertThat(parsed).containsKey("service").containsKey("log");
        assertThat(line).contains("openbake-test").contains("INFO");
    }

    @Test
    @DisplayName("MDC의 requestId가 JSON 필드로 나온다 — Loki에서 요청을 묶는 열쇠다")
    void includesMdcRequestId(CapturedOutput output) throws Exception {
        MDC.put(RequestIdMdcFilter.MDC_KEY, "smoke-test-request-id");
        try {
            log.info("MDC 포함 확인");
        } finally {
            MDC.remove(RequestIdMdcFilter.MDC_KEY);
        }

        String line = lastJsonLine(output, "MDC 포함 확인");
        assertThat(line).isNotNull();
        assertThat(line)
                .as("requestId가 JSON 어딘가에 있어야 한다")
                .contains("smoke-test-request-id");
    }

    @Test
    @DisplayName("민감 값이 마스킹된 채로 JSON에 들어간다")
    void masksSensitiveValues(CapturedOutput output) throws Exception {
        log.info("인증 실패 token=eyJhbGciOiJIUzI1NiJ9.secretpart");

        String line = lastJsonLine(output, "인증 실패");
        assertThat(line).isNotNull();
        assertThat(line)
                .as("커스터마이저가 실제로 적용되어야 한다")
                .doesNotContain("secretpart");
    }

    /** 캡처된 출력에서 해당 문구를 담은 마지막 JSON 줄을 찾는다. */
    private static String lastJsonLine(CapturedOutput output, String contains) {
        String found = null;
        for (String line : output.getOut().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("{") && trimmed.contains(contains)) {
                found = trimmed;
            }
        }
        return found;
    }
}
