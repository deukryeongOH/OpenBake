package com.openbake.product.infrastructure.ai;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 하이브리드 검색에서 의미 검색을 키워드 검색과 겹쳐 실행하기 위한 전용 풀.
 *
 * 공용 executor를 쓰지 않는 이유는 두 가지다.
 * 큐가 무제한이면 부하 시 요청이 큐에 쌓여 순차 실행보다 느려지고,
 * 다른 @Async 작업(ES 색인, VIEW 발행)과 스레드를 다투면 서로를 굶긴다.
 *
 * 큐가 차면 CallerRunsPolicy로 호출 스레드가 직접 실행한다.
 * 병렬성만 잃고 이슈 6 이전의 순차 동작으로 되돌아가므로 검색은 계속 성공한다.
 */
@Configuration
public class SearchExecutorConfig {

    @Bean("semanticSearchExecutor")
    public Executor semanticSearchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("semantic-search-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 종료 시 진행 중인 검색이 끊기지 않도록 짧게 기다린다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
