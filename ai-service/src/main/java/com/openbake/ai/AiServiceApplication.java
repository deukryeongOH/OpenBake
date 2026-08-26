package com.openbake.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.openbake.ai.application.InteractionProperties;
import com.openbake.ai.application.RecommendationProperties;
import com.openbake.ai.application.RecoveryProperties;
import com.openbake.ai.application.SemanticSearchProperties;

// common의 RequestIdMdcConfig를 쓰기 위해 함께 스캔한다. core·member·payment는
// base package가 com.openbake라 자동으로 포함되지만 ai만 com.openbake.ai다.
@SpringBootApplication(scanBasePackages = {"com.openbake.ai", "com.openbake.common.logging"})
@EnableScheduling
@EnableConfigurationProperties({
        InteractionProperties.class, RecommendationProperties.class, RecoveryProperties.class,
        SemanticSearchProperties.class})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
