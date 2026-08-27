package com.openbake.common.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 스케줄 배치가 여러 인스턴스에서 동시에 실행되지 않도록 막는 잠금(ShedLock) 설정.
 *
 * 저장소로 DB를 쓴다(docs/14 참고) — 이 프로젝트의 Redis는 재시작 시 데이터가 사라지도록
 * 설정돼 있어(재고 카운터는 drop_entry로 복구 가능하다는 전제, 05번 문서), 잠금처럼
 * "절대 사라지면 안 되는 정보"를 두기에는 맞지 않는다.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        // 애플리케이션 서버마다 시스템 시계가 조금씩 다를 수 있으므로, 잠금 만료 판단은
        // 서버 시계가 아니라 DB 시계(usingDbTime)를 기준으로 한다.
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}