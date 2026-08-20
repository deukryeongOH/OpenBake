package com.openbake.drop.infrastructure.adapter;

import com.openbake.drop.application.port.StockReservationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisStockReservationAdapter implements StockReservationPort {

    private static final String KEY_PREFIX = "drop:";
    private static final String KEY_SUFFIX = ":stock";

    private final StringRedisTemplate redisTemplate;

    // Lua 는 Redis 가 단일 스레드로 원자 실행하므로 검사와 차감 사이에 다른 요청이 끼어들 수 없다.
    private static final RedisScript<Long> RESERVE = load("redis/stock-reserve.lua");
    private static final RedisScript<Long> ROLLBACK = load("redis/stock-rollback.lua");

    private static RedisScript<Long> load(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    @Override
    public long reserve(Long dropId, int quantity) {
        return execute(RESERVE, key(dropId), String.valueOf(quantity));
    }

    @Override
    public long rollback(Long dropId, int quantity, int totalQuantity) {
        return execute(ROLLBACK, key(dropId), String.valueOf(quantity), String.valueOf(totalQuantity));
    }

    @Override
    public boolean initIfAbsent(Long dropId, int remainQuantity, Duration ttl) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key(dropId), String.valueOf(remainQuantity), ttl));
    }

    @Override
    public Long peek(Long dropId) {
        String value = redisTemplate.opsForValue().get(key(dropId));
        return value == null ? null : Long.valueOf(value);
    }

    @Override
    public void clear(Long dropId) {
        redisTemplate.delete(key(dropId));
    }

    /**
     * StringRedisTemplate 은 키·값 모두 문자열로 직렬화하므로 인자를 String 으로 넘기면 그대로 전달된다.
     * 반환값은 스크립트에 선언한 결과 타입(Long)으로 변환된다.
     */
    private long execute(RedisScript<Long> script, String key, String... args) {
        Long result = redisTemplate.execute(script, List.of(key), (Object[]) args);

        // 스크립트는 항상 정수를 반환한다. null 은 연결 이상 등 비정상 상황이므로 미초기화와 같이 fail-closed 로 처리한다.
        return result == null ? NOT_INITIALIZED : result;
    }

    private String key(Long dropId) {
        return KEY_PREFIX + dropId + KEY_SUFFIX;
    }
}