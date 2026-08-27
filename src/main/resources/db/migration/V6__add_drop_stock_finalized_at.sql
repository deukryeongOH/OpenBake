-- 드롭 종료 시 재고 최종 확정("정확히 1회")을 DB로 보장하기 위한 표식.
-- NULL이면 아직 확정 전, 값이 있으면 이미 확정됐다는 뜻이다.
-- 인스턴스가 여러 대일 때 각자 tryMarkEnded() 캐시 플래그(JVM 로컬)를 통과해
-- finalizeStock을 중복 실행할 수 있는데, 이 컬럼의 조건부 UPDATE가 실제 게이트 역할을 한다.

ALTER TABLE drops
    ADD COLUMN stock_finalized_at TIMESTAMP;