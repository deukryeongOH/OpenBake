package com.openbake.observability;

import com.openbake.product.infrastructure.elasticsearch.ProductSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * k3s 매니페스트(이슈 3)가 사용할 probe/metric endpoint가 실제로 응답하는지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class ActuatorEndpointTest {

    @MockitoBean
    private ProductSearchRepository productSearchRepository;

    @MockitoBean
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("/actuator/prometheus는 200과 application 태그가 붙은 metric text를 반환한다")
    void prometheusEndpoint_returnsMetricsWithApplicationTag() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
        assertThat(response.getBody()).contains("application=\"openbake\"");
    }

    @Test
    @DisplayName("/actuator/prometheus에 주문 적체 gauge가 노출된다")
    void prometheusEndpoint_exposesOrderBacklogGauges() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getBody())
                .contains("openbake_order_pending_expired")
                .contains("openbake_order_pending_oldest_age_seconds")
                .contains("openbake_order_active_slot_leaked");
    }


    @Test
    @DisplayName("/actuator/prometheus에 회로 차단기 상태가 노출된다")
    void prometheusEndpoint_exposesCircuitBreakerState() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);

        // ES·AI 장애를 격리하는 회로가 실제로 열렸는지는 이 지표로만 알 수 있다.
        // resilience4j-micrometer가 전이 의존성이라 명시적으로 선언되어 있지 않으므로,
        // 의존성 정리 과정에서 조용히 빠지면 이 테스트가 알려준다.
        assertThat(response.getBody())
                .contains("resilience4j_circuitbreaker_state")
                .contains("resilience4j_circuitbreaker_not_permitted_calls_total");
    }

    @Test
    @DisplayName("/actuator/health/liveness는 200을 반환한다")
    void livenessEndpoint_returnsOk() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health/liveness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("/actuator/health/readiness는 200을 반환한다")
    void readinessEndpoint_returnsOk() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health/readiness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
