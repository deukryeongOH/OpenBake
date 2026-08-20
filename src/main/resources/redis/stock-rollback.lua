-- 드롭 재고 선점 롤백
-- KEYS[1] = drop:{dropId}:stock
-- ARGV[1] = quantity
-- ARGV[2] = totalQuantity
--
-- 반환값
--   -1  키 없음 (미초기화)
--   -2  복구 후 총 수량 초과 (비정상 롤백)
--   >=0 복구 후 잔여 수량
local remain = redis.call('GET', KEYS[1])
if remain == false then
    return -1
end

remain = tonumber(remain)
local quantity = tonumber(ARGV[1])
local total = tonumber(ARGV[2])

if remain + quantity > total then
    return -2
end

return redis.call('INCRBY', KEYS[1], quantity)