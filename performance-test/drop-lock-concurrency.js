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

const lockTimeout =
    new Counter('drop_lock_timeout');

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

    // 200과 DR007 품절(400)은 responseCallback에서 정상 응답으로 취급합니다.
    http_req_failed: ['rate==0'],

    drop_lock_unexpected: ['count==0'],
    drop_lock_timeout: ['count==0'],
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
            maxDuration: '30s',
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
             * 서버 lock 대기 시간이 최대 3초이므로
             * HTTP timeout은 그보다 크게 둡니다.
             */
            timeout: '6s',
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
        errorCode === 'DR007';

    const isInvalidState =
        errorCode === 'DR013' ||
        errorCode === 'DR014' ||
        errorCode === 'DR011';

    /*
     * 모든 500을 lock timeout으로 분류하지 않습니다.
     * DR012만 lock timeout으로 집계합니다.
     */
    const isLockTimeout =
        errorCode === 'DR012';

    if (isSuccess) {
        lockSuccess.add(1);

    } else if (isSoldOut) {
        soldOut.add(1);

    } else if (isLockTimeout) {
        lockTimeout.add(1);

        console.error(
            [
                'LOCK_TIMEOUT',
                `memberId=${user.memberId}`,
                `status=${response.status}`,
                `code=${errorCode}`,
                `duration=${response.timings.duration}ms`,
                `body=${response.body}`,
            ].join(', '),
        );

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
        '재고 선점 성공 또는 정상 품절이다':
            () =>
                isSuccess || isSoldOut,

        '락 획득 시스템 오류가 아니다':
            () =>
                !isLockTimeout,
    });
}
