package com.openbake.drop.application.cache;

/**
 * 당일 드롭이 등록·수정·삭제되어 TodayDropCache를 다시 읽어야 함을 알리는 이벤트.
 *
 * DropService가 발행하고, 트랜잭션 커밋 이후에만 실제 Redis 전파로 이어져야 한다
 * (docs/11-drop-cache-invalidation-propagation.md 3.1절 — 커밋 전에 보내면
 * 다른 Pod가 아직 반영되지 않은 상태를 다시 읽는다).
 */
public record DropCacheInvalidatedEvent() {
}