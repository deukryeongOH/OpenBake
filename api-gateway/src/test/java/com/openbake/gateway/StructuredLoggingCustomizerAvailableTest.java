package com.openbake.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 게이트웨이의 로깅 설정이 클래스패스와 맞는지 확인한다.
 *
 * <p><b>왜 이 테스트가 있나.</b> 2026-08-27에 {@code application-prod.yml}의
 * {@code logging.structured.json.customizer}에 적은 클래스가 api-gateway
 * 클래스패스에 없어서 배포가 실패했다.
 *
 * <pre>
 * ClassNotFoundException: com.openbake.common.logging.SensitiveFieldMaskingCustomizer
 * </pre>
 *
 * <p>다른 네 서비스는 {@code common}을 의존해 문제가 없었고 게이트웨이만 없었다.
 * 그런데 {@code common}은 {@code api 'spring-boot-starter-webmvc'}를 노출하므로
 * <b>여기에 추가하면 Netty 대신 Tomcat이 떠서 게이트웨이가 통째로 깨진다.</b>
 * 그래서 서블릿 중립 모듈 {@code common-logging}으로 분리했다.
 *
 * <p>이 테스트는 두 가지를 지킨다.
 *
 * <ol>
 *   <li>설정에 적힌 클래스가 실제로 로드되는가 — 기동 실패를 컴파일·테스트 단계로 당긴다
 *   <li>서블릿 스택이 딸려 들어오지 않았는가 — 리액티브 게이트웨이가 유지되는가
 * </ol>
 *
 * <p>클래스 이름을 문자열로 쓰는 이유: {@code application-prod.yml}이 문자열로
 * 지정하므로, 여기서도 같은 문자열을 확인해야 설정과 코드가 어긋난 상태를 잡는다.
 * 타입으로 참조하면 이름을 바꿔도 테스트가 함께 따라가 버려 의미가 없다.
 */
class StructuredLoggingCustomizerAvailableTest {

    /** {@code application-prod.yml}의 logging.structured.json.customizer 값과 같아야 한다. */
    private static final String CUSTOMIZER_CLASS_NAME =
            "com.openbake.common.logging.SensitiveFieldMaskingCustomizer";

    @Test
    @DisplayName("prod 설정에 적힌 마스킹 클래스가 게이트웨이 클래스패스에 있다")
    void customizerClassIsOnClasspath() {
        assertThatCode(() -> Class.forName(CUSTOMIZER_CLASS_NAME))
                .as("application-prod.yml이 이 이름을 참조한다. 없으면 기동이 실패한다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서블릿 컨테이너가 클래스패스에 없다 — 있으면 Netty 대신 Tomcat이 뜬다")
    void servletContainerIsAbsent() {
        assertThat(present("org.apache.catalina.startup.Tomcat"))
                .as("common(starter-webmvc)이 딸려 들어왔을 수 있다")
                .isFalse();
        assertThat(present("org.springframework.web.servlet.DispatcherServlet"))
                .as("spring-webmvc가 딸려 들어왔을 수 있다")
                .isFalse();
    }

    @Test
    @DisplayName("리액티브 스택은 그대로 있다")
    void reactiveStackIsPresent() {
        assertThat(present("org.springframework.web.reactive.DispatcherHandler")).isTrue();
    }

    private static boolean present(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
