-- ShedLock 공식 스키마. 스케줄 배치가 여러 인스턴스에서 동시에 실행되지 않도록 막는
-- 잠금 정보를 저장한다.
--
-- Redis가 아니라 DB에 두는 이유(docs/14 참고): 이 프로젝트의 Redis는 재시작하면 안의
-- 데이터가 사라지도록 일부러 설정돼 있다(재고 카운터는 drop_entry로 다시 계산할 수 있어서
-- 괜찮지만, 잠금은 사라지면 안 된다). Redis가 재시작돼 잠금이 사라지는 순간 다른 인스턴스가
-- "아무도 안 잡고 있다"고 판단해 같은 배치를 또 실행할 수 있다. DB는 이 프로젝트에서
-- 재시작해도 데이터가 사라지지 않는 저장소라 잠금을 두기에 맞다.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);