-- 재고 선점(RESERVED 전환) 시각. 방치된 선점을 회수하는 만료 스위퍼의 기준값이다(docs/10 3.2절).
-- entry_time은 진입(ENTERED) 시각이라 재사용할 수 없어 별도 컬럼으로 둔다.

ALTER TABLE drop_entries
    ADD COLUMN reserved_at TIMESTAMP;