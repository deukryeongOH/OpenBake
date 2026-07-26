package com.openbake.seller.infrastructure;

import com.openbake.seller.domain.AccountVerificationRepository;
import com.openbake.seller.domain.AccountVerificationSession;
import com.openbake.seller.domain.VerifiedAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountVerificationRepositoryImpl implements AccountVerificationRepository {

    private static final String SESSION_KEY_PREFIX = "account-verification:";
    private static final String VERIFIED_KEY_PREFIX = "account-verified:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(3);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void saveSession(String verificationRequestId, AccountVerificationSession session) {
        redisTemplate.opsForValue().set(sessionKey(verificationRequestId), toJson(session), SESSION_TTL);
    }

    @Override
    public Optional<AccountVerificationSession> findSession(String verificationRequestId) {
        String value = redisTemplate.opsForValue().get(sessionKey(verificationRequestId));
        return Optional.ofNullable(value)
                .map(json -> objectMapper.readValue(json, AccountVerificationSession.class));
    }

    @Override
    public void deleteSession(String verificationRequestId) {
        redisTemplate.delete(sessionKey(verificationRequestId));
    }

    @Override
    public void saveVerifiedAccount(Long memberId, VerifiedAccount account) {
        redisTemplate.opsForValue().set(verifiedKey(memberId), toJson(account), VERIFIED_TTL);
    }

    @Override
    public Optional<VerifiedAccount> findVerifiedAccountByMemberId(Long memberId) {
        String value = redisTemplate.opsForValue().get(verifiedKey(memberId));
        return Optional.ofNullable(value)
                .map(json -> objectMapper.readValue(json, VerifiedAccount.class));
    }

    private String sessionKey(String verificationRequestId) {
        return SESSION_KEY_PREFIX + verificationRequestId;
    }

    private String verifiedKey(Long memberId) {
        return VERIFIED_KEY_PREFIX + memberId;
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
