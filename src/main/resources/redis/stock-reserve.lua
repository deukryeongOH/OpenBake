-- 드롭 재고 원자적 선점
-- KEYS[1] = drop:{dropId}:stock
-- ARGV[1] = quantity
--
-- 반환값
--   -1  키 없음 (미초기화) → fail-closed
--   -2  재고 부족
--    0  마지막 재고를 선점함 (호출자가 품절 처리)
--   >0  선점 후 잔여 수량
--
-- DECRBY 후 음수면 INCRBY 로 되돌리는 방식은 쓰지 않는다.
-- 되돌리는 사이 정상적으로 구매 가능한 요청이 품절로 잘못 거부된다.
local remain = redis.call('GET', KEYS[1])
if remain == false then
    return -1
end

remain = tonumber(remain)
local quantity = tonumber(ARGV[1])

if remain < quantity then
    return -2
end

return redis.call('DECRBY', KEYS[1], quantity)