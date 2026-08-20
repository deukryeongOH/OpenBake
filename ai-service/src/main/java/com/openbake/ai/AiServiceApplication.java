package com.openbake.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.openbake.ai.application.InteractionProperties;
import com.openbake.ai.application.RecommendationProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({InteractionProperties.class, RecommendationProperties.class})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
