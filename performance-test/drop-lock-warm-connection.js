/*
 * drop-lock-concurrency.js 의 변형. 측정 전에 연결을 먼저 세운다.
 *
 * 왜 필요한가
 *   원본은 per-vu-iterations(vus=USER_COUNT, iterations=1)이라 VU 하나가 요청
 *   하나만 보낸다. 그래서 300 동시 부하는 곧 **새 TLS 연결 300개**를 뜻한다.
 *   2026-08-26 실측에서 이 핸드셰이크가 지연의 42%를 차지했고, 그 원인은
 *   인증서가 RSA 4096 이라 서명 연산이 초당 186회밖에 안 되기 때문이었다
 *   (같은 노드에서 ECDSA P-256 은 초당 28,201회).
 *
 *   그런데 실제 드롭 오픈에서 사용자는 이미 페이지를 열어둔 상태다. 브라우저는
 *   ALPN h2 로 연결을 재사용하므로 핸드셰이크는 버스트 이전에 끝나 있다.
 *   즉 원본 스크립트는 최악의 경우를 만든다.
 *
 * 이 스크립트가 하는 일
 *   1) 조회 요청 한 번으로 연결을 세운다 (측정 제외)
 *   2) 모든 VU 의 핸드셰이크가 끝나도록 잠깐 기다린다
 *   3) 그 연결 위에서 lock-start 를 보낸다 (측정)
 *
 *   원본과의 차이는 오직 "연결이 이미 있는가"뿐이다. 두 결과의 차이가 곧
 *   핸드셰이크 몫이다.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { getUserForVu, users } from './k6-users.js';
import { getAuthHeaders } from './k6-auth.js';

const CORE_BASE_URL = __ENV.CORE_BASE_URL ?? 'http://localhost:8080';
const DROP_ID = Number(__ENV.DROP_ID ?? 0);
const USER_COUNT = Number(__ENV.USER_COUNT ?? 0);
const QUANTITY = Number(__ENV.QUANTITY ?? 1);
const EXPECTED_SUCCESS = Number(__ENV.EXPECTED_SUCCESS ?? USER_COUNT);
const EXPECTED_SOLD_OUT = Number(__ENV.EXPECTED_SOLD_OUT ?? 0);
const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT ?? '30s';
const MAX_DURATION = __ENV.MAX_DURATION ?? '120s';

// 모든 VU 가 연결을 세울 때까지 기다리는 시간. 300개 핸드셰이크가 끝나야
// POST 버스트가 동시에 출발한다. 너무 짧으면 핸드셰이크가 측정 구간으로 샌다.
const CONNECT_SETTLE = Number(__ENV.CONNECT_SETTLE ?? 5);

if (!Number.isInteger(DROP_ID) || DROP_ID <= 0) {
    throw new Error(`DROP_ID는 1 이상의 정수여야 합니다. 현재값=${DROP_ID}`);
}
if (USER_COUNT > users.length) {
    throw new Error(`USER_COUNT=${USER_COUNT}, users.json=${users.length}. 사용자 부족.`);
}

const lockSuccess = new Counter('drop_lock_success');
const soldOut = new Counter('drop_lock_sold_out');
const invalidState = new Counter('drop_lock_invalid_state');
const unexpected = new Counter('drop_lock_unexpected');
const businessDuration = new Trend('drop_lock_business_duration', true);
// 연결 수립 구간. 측정 대상은 아니지만 얼마나 걸렸는지는 남긴다.
const connectDuration = new Trend('connection_setup_duration', true);

export const options = {
    scenarios: {
        warm_connection_reservation: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: MAX_DURATION,
        },
    },
    thresholds: {
        checks: ['rate==1'],
        drop_lock_unexpected: ['count==0'],
        drop_lock_invalid_state: ['count==0'],
        drop_lock_success: [`count==${EXPECTED_SUCCESS}`],
        drop_lock_business_duration: ['p(95)<1500', 'p(99)<3000'],
    },
};

function extractErrorCode(response) {
    if (!response.body) return '';
    try {
        const body = response.json();
        return body.errorCode ?? body.code ?? body.data?.errorCode ?? body.error?.code ?? '';
    } catch {
        return '';
    }
}

export default function () {
    const user = getUserForVu(__VU);

    // 1) 연결 수립. 상태를 바꾸지 않는 조회를 쓴다.
    const warm = http.get(
        `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/info`,
        {
            headers: { Accept: 'application/json', ...getAuthHeaders(user) },
            tags: { api_name: 'connection-setup' },
            timeout: HTTP_TIMEOUT,
        },
    );
    connectDuration.add(warm.timings.duration);

    // 2) 300개 핸드셰이크가 모두 끝나도록 대기. 이 뒤부터 연결은 재사용된다.
    sleep(CONNECT_SETTLE);

    // 3) 측정 대상. 같은 연결 위에서 나간다.
    const response = http.post(
        `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/lock-start`,
        JSON.stringify({ quantity: QUANTITY }),
        {
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
                ...getAuthHeaders(user),
            },
            responseCallback: http.expectedStatuses(200, 400),
            tags: { api_name: 'drop-lock-start', test_type: 'warm-connection' },
            timeout: HTTP_TIMEOUT,
        },
    );

    businessDuration.add(response.timings.duration);

    const errorCode = extractErrorCode(response);
    const isSuccess = response.status === 200;
    const isSoldOut = response.status === 400 && (errorCode === 'DR007' || errorCode === 'DR018');
    const isInvalidState = errorCode === 'DR013' || errorCode === 'DR014' || errorCode === 'DR011';

    if (isSuccess) {
        lockSuccess.add(1);
    } else if (isSoldOut) {
        soldOut.add(1);
    } else if (isInvalidState) {
        invalidState.add(1);
        console.error(`INVALID_STATE, memberId=${user.memberId}, code=${errorCode}`);
    } else {
        unexpected.add(1);
        console.error(`UNEXPECTED, memberId=${user.memberId}, status=${response.status}, code=${errorCode}`);
    }

    check(response, {
        '재고 선점 성공 또는 정상 재고 부족이다': () => isSuccess || isSoldOut,
    });
}
