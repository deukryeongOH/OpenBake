import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { getUserForVu, users } from './k6-users.js';
import { getAuthHeaders } from './k6-auth.js';

const CORE_BASE_URL =
    __ENV.CORE_BASE_URL ?? 'http://localhost:8080';

const DROP_ID =
    Number(__ENV.DROP_ID ?? 0);

const USER_COUNT =
    Number(__ENV.USER_COUNT ?? 0);

const QUANTITY =
    Number(__ENV.QUANTITY ?? 0);

/*
 * 예:
 * 재고 5개 / 사용자 10명 / 각 1개 요청
 *
 * EXPECTED_SUCCESS=5
 * EXPECTED_SOLD_OUT=5
 */
const EXPECTED_SUCCESS =
    Number(__ENV.EXPECTED_SUCCESS ?? USER_COUNT);

const EXPECTED_SOLD_OUT =
    Number(__ENV.EXPECTED_SOLD_OUT ?? 0);

/*
 * 요청 타임아웃 / 시나리오 상한
 *
 * 기존에는 5s(lock은 10s)로 하드코딩돼 있어 서버가 그보다 느리면
 * k6가 먼저 끊어버리고 status=0으로 기록됐습니다.
 * 그러면 실제 응답시간이 얼마인지 알 수 없으므로 환경변수로 분리합니다.
 *
 * MAX_DURATION은 HTTP_TIMEOUT보다 반드시 커야 합니다.
 * 작으면 시나리오가 먼저 끊겨 interrupted iterations로 데이터가 유실됩니다.
 */
const HTTP_TIMEOUT =
    __ENV.HTTP_TIMEOUT ?? '30s';

const MAX_DURATION =
    __ENV.MAX_DURATION ?? '120s';

/*
 * 환경변수 검증
 */
if (!Number.isInteger(DROP_ID) || DROP_ID <= 0) {
    throw new Error(
        `DROP_ID는 1 이상의 정수여야 합니다. 현재값=${DROP_ID}`
    );
}

if (!Number.isInteger(USER_COUNT) || USER_COUNT <= 0) {
    throw new Error(
        `USER_COUNT는 1 이상의 정수여야 합니다. 현재값=${USER_COUNT}`
    );
}

if (!Number.isInteger(QUANTITY) || QUANTITY <= 0) {
    throw new Error(
        `QUANTITY는 1 이상의 정수여야 합니다. 현재값=${QUANTITY}`
    );
}

if (
    !Number.isInteger(EXPECTED_SUCCESS) ||
    EXPECTED_SUCCESS < 0
) {
    throw new Error(
        `EXPECTED_SUCCESS는 0 이상의 정수여야 합니다. ` +
        `현재값=${EXPECTED_SUCCESS}`
    );
}

if (
    !Number.isInteger(EXPECTED_SOLD_OUT) ||
    EXPECTED_SOLD_OUT < 0
) {
    throw new Error(
        `EXPECTED_SOLD_OUT은 0 이상의 정수여야 합니다. ` +
        `현재값=${EXPECTED_SOLD_OUT}`
    );
}

if (
    EXPECTED_SUCCESS + EXPECTED_SOLD_OUT !==
    USER_COUNT
) {
    throw new Error(
        `EXPECTED_SUCCESS(${EXPECTED_SUCCESS}) + ` +
        `EXPECTED_SOLD_OUT(${EXPECTED_SOLD_OUT})는 ` +
        `USER_COUNT(${USER_COUNT})와 같아야 합니다.`
    );
}

if (USER_COUNT > users.length) {
    throw new Error(
        `USER_COUNT=${USER_COUNT}, users.json=${users.length}. ` +
        `테스트 사용자 수가 부족합니다.`
    );
}

/*
 * 사용자 정의 Metric
 */
const lockSuccess =
    new Counter('drop_lock_success');

const soldOut =
    new Counter('drop_lock_sold_out');

const invalidState =
    new Counter('drop_lock_invalid_state');

const unexpected =
    new Counter('drop_lock_unexpected');

const businessDuration =
    new Trend(
        'drop_lock_business_duration',
        true,
    );

/*
 * EXPECTED_SOLD_OUT가 1 이상인 초과 판매 시나리오에서는
 * 품절 건수도 정확하게 검증합니다.
 *
 * EXPECTED_SOLD_OUT=0인 기본 성공 시나리오에서는
 * success==USER_COUNT가 이미 품절 발생 여부까지 검증합니다.
 */
const thresholds = {
    // checks는 기능 정합성만 검증하고, 응답시간은 Trend threshold로 분리합니다.
    checks: ['rate==1'],

    // 200과 기대 가능한 재고 부족 400은 responseCallback에서 정상 응답으로 취급합니다.
    http_req_failed: ['rate==0'],

    drop_lock_unexpected: ['count==0'],
    drop_lock_invalid_state: ['count==0'],

    drop_lock_success: [
        `count==${EXPECTED_SUCCESS}`,
    ],

    drop_lock_business_duration: [
        'p(95)<1500',
        'p(99)<3000',
    ],
};

if (EXPECTED_SOLD_OUT > 0) {
    thresholds.drop_lock_sold_out = [
        `count==${EXPECTED_SOLD_OUT}`,
    ];
}

export const options = {
    scenarios: {
        concurrent_stock_reservation: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: MAX_DURATION,
        },
    },

    thresholds,
};

function extractErrorCode(response) {
    if (!response.body) {
        return '';
    }

    try {
        const body = response.json();

        return (
            body.errorCode ??
            body.code ??
            body.data?.errorCode ??
            body.error?.code ??
            ''
        );
    } catch {
        return '';
    }
}

export default function () {
    /*
     * enter, confirm-entry와 동일한 사용자 매핑 사용
     */
    const user = getUserForVu(__VU);

    const requestBody = JSON.stringify({
        quantity: QUANTITY,
    });

    const response = http.post(
        `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/lock-start`,
        requestBody,
        {
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
                ...getAuthHeaders(user),
            },

            /*
             * 200과 정상 품절 400은
             * 시나리오상 기대 가능한 응답입니다.
             */
            responseCallback: http.expectedStatuses(
                200,
                400,
            ),

            tags: {
                api_name: 'drop-lock-start',
                test_type: 'stock-concurrency',
            },

            /*
             * DB row contention과 connection 대기를 포함한 상한입니다.
             */
            timeout: HTTP_TIMEOUT,
        },
    );

    businessDuration.add(
        response.timings.duration
    );

    const errorCode =
        extractErrorCode(response);

    const isSuccess =
        response.status === 200;

    const isSoldOut =
        response.status === 400 &&
        (errorCode === 'DR007' || errorCode === 'DR018');

    const isInvalidState =
        errorCode === 'DR013' ||
        errorCode === 'DR014' ||
        errorCode === 'DR011';


    if (isSuccess) {
        lockSuccess.add(1);

    } else if (isSoldOut) {
        soldOut.add(1);

    } else if (isInvalidState) {
        invalidState.add(1);

        console.error(
            [
                'INVALID_STATE',
                `memberId=${user.memberId}`,
                `status=${response.status}`,
                `code=${errorCode}`,
                `body=${response.body}`,
            ].join(', '),
        );

    } else {
        unexpected.add(1);

        console.error(
            [
                'UNEXPECTED',
                `memberId=${user.memberId}`,
                `status=${response.status}`,
                `code=${errorCode}`,
                `body=${response.body}`,
            ].join(', '),
        );
    }

    check(response, {
        '재고 선점 성공 또는 정상 재고 부족이다':
            () =>
                isSuccess || isSoldOut,

    });
}
