import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Gauge, Trend } from 'k6/metrics';
import { getUserForVu, users } from './k6-users.js';
import { getAuthHeaders } from './k6-auth.js';

/*
 * 초과 판매(oversell) 검증 전용 시나리오.
 *
 * drop-lock-concurrency.js 와 다른 점은 "성공 개수가 맞는가"에서 그치지 않고,
 * 드롭 조회 API로 실제 재고를 읽어 대조한다는 것이다.
 * 클라이언트가 센 성공 응답 수와 서버가 실제로 판 수량이 일치하고,
 * 잔여 수량이 음수로 내려가지 않아야 초과 판매가 없다고 말할 수 있다.
 *
 * 재고보다 많은 사용자를 동시에 붙여야 의미가 있다. (USER_COUNT > 재고)
 */

const CORE_BASE_URL = __ENV.CORE_BASE_URL ?? 'http://localhost:8080';
const DROP_ID = Number(__ENV.DROP_ID ?? 0);
const USER_COUNT = Number(__ENV.USER_COUNT ?? 0);
const QUANTITY = Number(__ENV.QUANTITY ?? 1);

const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT ?? '30s';
const MAX_DURATION = __ENV.MAX_DURATION ?? '120s';

// 잔여 수량은 DropScheduler.syncDropStock 이 2초마다 Redis -> DB 로 반영한다.
// 최종 상태를 읽기 전에 그 주기보다 넉넉히 기다린다.
const SETTLE_SECONDS = Number(__ENV.SETTLE_SECONDS ?? 6);

if (!Number.isInteger(DROP_ID) || DROP_ID <= 0) {
    throw new Error(`DROP_ID는 1 이상의 정수여야 합니다. 현재값=${DROP_ID}`);
}
if (!Number.isInteger(USER_COUNT) || USER_COUNT <= 0) {
    throw new Error(`USER_COUNT는 1 이상의 정수여야 합니다. 현재값=${USER_COUNT}`);
}
if (!Number.isInteger(QUANTITY) || QUANTITY <= 0) {
    throw new Error(`QUANTITY는 1 이상의 정수여야 합니다. 현재값=${QUANTITY}`);
}
if (USER_COUNT > users.length) {
    throw new Error(`USER_COUNT=${USER_COUNT}, users.json=${users.length}. 테스트 사용자 수가 부족합니다.`);
}

/* 응답 분류 */
const lockSuccess = new Counter('lock_success');
const lockSoldOut = new Counter('lock_sold_out');            // DR007 재고 소진
const lockNotEntered = new Counter('lock_not_entered');      // DR014 ENTERED 상태 아님
const lockDuplicate = new Counter('lock_duplicate_request'); // C004 동시 요청 충돌
const lockStockNotInit = new Counter('lock_stock_not_init'); // DR022 카운터 미초기화
const lockUnexpected = new Counter('lock_unexpected');

const lockDuration = new Trend('lock_business_duration', true);

/* 초과 판매 판정 지표 — teardown 에서 채운다 */
const stockBefore = new Gauge('stock_remain_before');
const stockAfter = new Gauge('stock_remain_after');
const soldByServer = new Gauge('sold_by_server');       // 서버 재고 감소분
const oversellUnits = new Gauge('oversell_units');      // 재고를 넘겨 판 수량. 0이어야 한다
// 서버·클라이언트 수량 대조는 teardown 에서 성공 응답 수를 알 수 없으므로
// handleSummary 에서 계산해 표시만 한다(threshold 로는 걸 수 없다).

export const options = {
    scenarios: {
        oversell_burst: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: MAX_DURATION,
        },
    },
    thresholds: {
        // 이 셋이 초과 판매 없음의 핵심 근거다.
        oversell_units: ['value==0'],
        stock_remain_after: ['value>=0'],

        // 정합성 오류는 0이어야 한다. 재고 소진(DR007)은 정상 결과이므로 제외한다.
        lock_unexpected: ['count==0'],
        lock_stock_not_init: ['count==0'],

        // 응답시간은 참고용이라 실패시키지 않는다(abortOnFail 없음).
        lock_business_duration: ['p(95)<3000'],
    },
};

export function setup() {
    const res = http.get(`${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/info`, {
        headers: { Accept: 'application/json' },
        tags: { api_name: 'drop-info' },
    });

    if (res.status !== 200) {
        throw new Error(`드롭 조회 실패. status=${res.status}, body=${res.body}`);
    }

    const total = res.json('data.totalQuantity');
    const remain = res.json('data.remainQuantity');
    const status = res.json('data.dropStatus');

    if (status !== 'ACTIVE') {
        throw new Error(`드롭이 ACTIVE가 아닙니다. status=${status}. prepare-drop.sh로 활성화하세요.`);
    }

    const capacity = Math.floor(remain / QUANTITY);
    if (USER_COUNT <= capacity) {
        throw new Error(
            `초과 판매 검증은 재고보다 많은 사용자가 필요합니다. ` +
            `USER_COUNT=${USER_COUNT}, 구매 가능 인원=${capacity}(remain=${remain}, quantity=${QUANTITY})`,
        );
    }

    console.log(`[SETUP] dropId=${DROP_ID} total=${total} remain=${remain} users=${USER_COUNT} quantity=${QUANTITY}`);
    console.log(`[SETUP] 기대: 성공 ${capacity}명, 품절 ${USER_COUNT - capacity}명`);

    return { remainBefore: remain, totalQuantity: total, capacity };
}

export default function () {
    const user = getUserForVu(__VU);

    const response = http.post(
        `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/lock-start`,
        JSON.stringify({ quantity: QUANTITY }),
        {
            headers: {
                'Content-Type': 'application/json',
                Accept: 'application/json',
                ...getAuthHeaders(user),
            },
            tags: { api_name: 'drop-lock-start', test_type: 'oversell-verification' },
            timeout: HTTP_TIMEOUT,
        },
    );

    lockDuration.add(response.timings.duration);

    let errorCode = null;
    try {
        errorCode = response.json('error.code');
    } catch (e) {
        errorCode = null;
    }

    if (response.status === 200) {
        lockSuccess.add(1);
    } else if (errorCode === 'DR007') {
        lockSoldOut.add(1);
    } else if (errorCode === 'DR014') {
        lockNotEntered.add(1);
    } else if (errorCode === 'C004') {
        lockDuplicate.add(1);
    } else if (errorCode === 'DR022') {
        lockStockNotInit.add(1);
    } else {
        lockUnexpected.add(1);
        console.error(`UNEXPECTED memberId=${user.memberId} status=${response.status} code=${errorCode} body=${response.body}`);
    }

    check(response, {
        '응답이 성공(200) 또는 품절(DR007)이다': (res) => res.status === 200 || errorCode === 'DR007',
    });
}

export function teardown(data) {
    // Redis -> DB 동기화(2초 주기)가 최종값을 반영할 때까지 기다린다.
    sleep(SETTLE_SECONDS);

    const res = http.get(`${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/info`, {
        headers: { Accept: 'application/json' },
        tags: { api_name: 'drop-info' },
    });

    if (res.status !== 200) {
        console.error(`[TEARDOWN] 드롭 조회 실패로 초과 판매 검증 불가. status=${res.status}`);
        return;
    }

    const remainAfter = res.json('data.remainQuantity');
    const soldServer = data.remainBefore - remainAfter;

    stockBefore.add(data.remainBefore);
    stockAfter.add(remainAfter);
    soldByServer.add(soldServer);

    // 서버가 판 수량이 원래 재고를 넘었는가. 음수 재고와 같은 뜻이다.
    oversellUnits.add(Math.max(soldServer - data.remainBefore, 0));

    console.log('');
    console.log('================ 초과 판매 검증 ================');
    console.log(`드롭 ID           : ${DROP_ID}`);
    console.log(`시작 잔여 재고    : ${data.remainBefore}`);
    console.log(`종료 잔여 재고    : ${remainAfter}`);
    console.log(`서버 판매 수량    : ${soldServer}`);
    console.log(`구매 가능 인원    : ${data.capacity}`);
    console.log('----------------------------------------------');
    console.log(`잔여 재고 음수 여부 : ${remainAfter < 0 ? '❌ 발생 (초과 판매)' : '✅ 없음'}`);
    console.log(`재고 초과 판매량    : ${Math.max(soldServer - data.remainBefore, 0)}`);
    console.log('==============================================');
    console.log('클라이언트 성공 수와의 대조는 아래 요약 블록을 보세요.');
}

/*
 * k6 summary 를 그대로 두면 지표가 흩어져 있어 판정이 어렵다.
 * 두 가지 질문에 바로 답하도록 요약 블록을 앞에 붙인다.
 */
export function handleSummary(data) {
    const count = (n) => (data.metrics[n] && data.metrics[n].values.count) || 0;
    const gauge = (n) => (data.metrics[n] && data.metrics[n].values.value) || 0;
    const stat = (n, k) => (data.metrics[n] && data.metrics[n].values[k]) || 0;
    const rate = (n) => (data.metrics[n] && data.metrics[n].values.rate) || 0;

    const success = count('lock_success');
    const soldOut = count('lock_sold_out');
    const clientSold = success * QUANTITY;
    const serverSold = gauge('sold_by_server');
    const remainBefore = gauge('stock_remain_before');
    const remainAfter = gauge('stock_remain_after');
    const mismatch = Math.abs(serverSold - clientSold);
    const oversell = Math.max(serverSold - remainBefore, 0);
    const unexpected = count('lock_unexpected');

    const ok = (b) => (b ? 'PASS' : 'FAIL');
    const ms = (v) => `${v.toFixed(0)}ms`;

    // threshold 통과 여부
    const failed = [];
    for (const [name, m] of Object.entries(data.metrics)) {
        if (!m.thresholds) continue;
        for (const [expr, t] of Object.entries(m.thresholds)) {
            if (t.ok === false) failed.push(`${name}: ${expr}`);
        }
    }

    const L = [
        '',
        '==================================================================',
        ' 1) 초과 판매 검증',
        '==================================================================',
        `  동시 요청     : ${USER_COUNT}명 (1인당 ${QUANTITY}개)`,
        `  재고          : ${remainBefore} -> ${remainAfter}   (서버 판매 ${serverSold})`,
        '',
        `  성공(200)             : ${success}`,
        `  품절(DR007)           : ${soldOut}          <- 정상 결과`,
        `  미입장(DR014)         : ${count('lock_not_entered')}`,
        `  동시요청충돌(C004)    : ${count('lock_duplicate_request')}`,
        `  카운터미초기화(DR022) : ${count('lock_stock_not_init')}`,
        `  예상밖 오류           : ${unexpected}`,
        '',
        `  [${ok(oversell === 0)}] 재고 초과 판매 없음        (초과분 ${oversell})`,
        `  [${ok(remainAfter >= 0)}] 잔여 재고 음수 아님        (${remainAfter})`,
        `  [${ok(mismatch === 0)}] 서버-클라이언트 수량 일치  (서버 ${serverSold} / 응답 ${clientSold})`,
        `  [${ok(unexpected === 0)}] 예상밖 오류 없음`,
        '',
        '==================================================================',
        ' 2) 용량 지표 (다중 인스턴스 판단용)',
        '==================================================================',
        `  처리량        : ${rate('iterations').toFixed(1)} req/s   (총 ${count('iterations')}건)`,
        '',
        `  전체 응답시간  p95 ${ms(stat('http_req_duration', 'p(95)'))}   p99 ${ms(stat('http_req_duration', 'p(99)'))}   max ${ms(stat('http_req_duration', 'max'))}`,
        `   ├ 서버 처리   p95 ${ms(stat('http_req_waiting', 'p(95)'))}      <- 크면 서버 내부 병목`,
        `   ├ 커넥션 수립 p95 ${ms(stat('http_req_connecting', 'p(95)'))}`,
        `   └ 대기(blocked) p95 ${ms(stat('http_req_blocked', 'p(95)'))}    <- 크면 커넥션 한계`,
        '',
        `  lock-start만  p95 ${ms(stat('lock_business_duration', 'p(95)'))}   p99 ${ms(stat('lock_business_duration', 'p(99)'))}`,
        `  실패율        : ${(rate('http_req_failed') * 100).toFixed(2)}%`,
        '',
        '  판정은 서버 지표와 함께 봐야 한다:',
        '    앱 CPU 포화        -> 스케일아웃 효과 있음',
        '    hikari_pending > 0 -> 커넥션 풀 조정이 먼저',
        '    앱 CPU 낮은데 느림 -> Redis/DB 직렬화. 스케일아웃 무의미',
        '  python3 capacity/collect-observability.py 로 서버 지표를 함께 수집할 것.',
        '',
        '==================================================================',
        failed.length === 0
            ? '  모든 threshold 통과'
            : `  실패한 threshold (${failed.length}): ` + failed.join(', '),
        '==================================================================',
        '',
    ];

    const NL = String.fromCharCode(10);
    return { stdout: L.join(NL) + NL };
}
