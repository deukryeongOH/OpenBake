-- Order가 결제 차감 멱등키를 발급하기 위한 현재 시도 번호와 전이 표식.
-- 정상 FAIL 응답에서는 번호를 유지하고, 사용자가 다음 결제를 시작할 때만 증가한다.

ALTER TABLE orders
    ADD COLUMN pay_attempt_no INTEGER NOT NULL DEFAULT 1;

ALTER TABLE orders
    ADD COLUMN advance_pay_attempt_on_next_request BOOLEAN NOT NULL DEFAULT FALSE;
