import http from 'k6/http';
import { check, sleep } from 'k6';
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
    Number(__ENV.QUANTITY ?? 1);

const POLL_INTERVAL_MS =
    Number(__ENV.WAIT_ACTIVE_POLL_MS ?? 200);

const TIMEOUT_SECONDS =
    Number(__ENV.WAIT_ACTIVE_TIMEOUT_SECONDS ?? 30);

/*
 * Drop 사용자 흐름
 *
 * 1. 드롭 상세 조회
 * 2. 대기열 진입
 * 3. 내 순번 polling (ACTIVE가 될 때까지)
 * 4. 입장 확정
 * 5. 재고 선점
 *
 * 기존 drop-enter / drop-wait-active / drop-confirm-entry /
 * drop-lock-concurrency 테스트의 요청/검증 방식을 한 VU 흐름으로 묶었습니다.
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

if (USER_COUNT > users.length) {
    throw new Error(
        `USER_COUNT=${USER_COUNT}, users.json=${users.length}. ` +
        '테스트 사용자 수가 부족합니다.'
    );
}

const flowSuccess =
    new Counter('drop_user_flow_success');

const flowFailed =
    new Counter('drop_user_flow_failed');

const infoDuration =
    new Trend('drop_flow_info_duration', true);

const enterDuration =
    new Trend('drop_flow_enter_duration', true);

const waitDuration =
    new Trend('drop_flow_wait_active_duration', true);

const confirmDuration =
    new Trend('drop_flow_confirm_duration', true);

const lockDuration =
    new Trend('drop_flow_lock_duration', true);

const totalDuration =
    new Trend('drop_flow_total_duration', true);

export const options = {
    scenarios: {
        drop_user_flow: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: `${TIMEOUT_SECONDS + 20}s`,
        },
    },

    thresholds: {
        checks: ['rate==1'],
        drop_user_flow_success: [`count==${USER_COUNT}`],
        drop_user_flow_failed: ['count==0'],

        drop_flow_info_duration: [
            'p(95)<1000',
            'p(99)<2000',
        ],

        drop_flow_enter_duration: [
            'p(95)<1000',
            'p(99)<2000',
        ],

        drop_flow_confirm_duration: [
            'p(95)<1000',
            'p(99)<2000',
        ],

        drop_flow_lock_duration: [
            'p(95)<1500',
            'p(99)<3000',
        ],
    },
};

function isSuccessResponse(response) {
    if (response.status !== 200) {
        return false;
    }

    try {
        return response.json('success') === true;
    } catch {
        return false;
    }
}

function extractErrorCode(response) {
    if (!response || !response.body) {
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

function parseQueue(response) {
    try {
        const body = response.json();
        const data = body?.data;

        return {
            success: body?.success === true,
            rank:
                data?.rank !== undefined && data?.rank !== null
                    ? Number(data.rank)
                    : null,
            status: data?.status ?? null,
            code: body?.error?.code ?? null,
        };
    } catch {
        return {
            success: false,
            rank: null,
            status: null,
            code: null,
        };
    }
}

function failFlow(step, user, response, extra = '') {
    flowFailed.add(1);

    const status = response?.status ?? '-';
    const errorCode = response ? extractErrorCode(response) : '';
    const body = response?.body ?? '';

    console.error(
        [
            'DROP_USER_FLOW_FAILED',
            `step=${step}`,
            `memberId=${user.memberId}`,
            `status=${status}`,
            `code=${errorCode}`,
            extra,
            `body=${body}`,
        ]
            .filter((value) => value !== '')
            .join(', '),
    );
}

export default function () {
    const user = getUserForVu(__VU);
    const startedAt = Date.now();

    const headers = {
        Accept: 'application/json',
        ...getAuthHeaders(user),
    };

    /*
     * 1. 드롭 상세 조회
     * 실제 사용자가 드롭 상세 화면에 들어오는 단계입니다.
     */
    const infoResponse = http.get(
        `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/info`,
        {
            headers,
            tags: {
                api_name: 'drop-info',
                test_type: 'drop-user-flow',
                flow_step: '01-info',
            },
            timeout: '5s',
        },
    );

    infoDuration.add(infoResponse.timings.duration);

    const infoSuccess = isSuccessResponse(infoResponse);

    if (!check(infoResponse, {
        '01 드롭 상세 조회가 성공한다': () => infoSuccess,
    })) {
        failFlow('01-info', user, infoResponse);
        return;
    }

    /*
     * 2. 대기열 진입
     * 기존 drop-enter-concurrency.js와 같은 API/인증을 사용합니다.
     */
    const enterResponse = http.post(
        `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/enter`,
        null,
        {
            headers,
            tags: {
                api_name: 'drop-enter',
                test_type: 'drop-user-flow',
                flow_step: '02-enter',
            },
            timeout: '5s',
        },
    );

    enterDuration.add(enterResponse.timings.duration);

    const enterSuccess = isSuccessResponse(enterResponse);

    if (!check(enterResponse, {
        '02 드롭 대기열 진입이 성공한다': () => enterSuccess,
    })) {
        failFlow('02-enter', user, enterResponse);
        return;
    }

    /*
     * 3. ACTIVE가 될 때까지 내 순번 조회
     * QueueScheduler가 200ms마다 최대 100명을 ACTIVE로 이동시키므로
     * 실제 프론트처럼 polling합니다.
     */
    const waitStartedAt = Date.now();
    const deadline = waitStartedAt + TIMEOUT_SECONDS * 1000;

    let active = false;
    let lastRank = null;
    let lastQueueStatus = null;
    let lastResponse = null;

    while (Date.now() < deadline) {
        const rankResponse = http.get(
            `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/queue/rank`,
            {
                headers,
                tags: {
                    api_name: 'drop-queue-rank',
                    test_type: 'drop-user-flow',
                    flow_step: '03-wait-active',
                },
                timeout: '5s',
            },
        );

        lastResponse = rankResponse;

        const queue = parseQueue(rankResponse);
        lastRank = queue.rank;
        lastQueueStatus = queue.status;

        if (
            rankResponse.status === 200 &&
            queue.success &&
            queue.rank === 0 &&
            queue.status === 'ACTIVE'
        ) {
            active = true;
            break;
        }

        if (rankResponse.status !== 200 || !queue.success) {
            break;
        }

        sleep(POLL_INTERVAL_MS / 1000);
    }

    waitDuration.add(Date.now() - waitStartedAt);

    if (!check(active, {
        '03 사용자가 제한시간 내 ACTIVE가 된다': () => active,
    })) {
        failFlow(
            '03-wait-active',
            user,
            lastResponse,
            `rank=${lastRank}, queueStatus=${lastQueueStatus}`,
        );
        return;
    }

    /*
     * 4. 입장 확정
     * 기존 drop-confirm-entry-concurrency.js의 요청을 그대로 이어갑니다.
     */
    const confirmResponse = http.post(
        `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/confirm-entry`,
        null,
        {
            headers,
            tags: {
                api_name: 'drop-confirm-entry',
                test_type: 'drop-user-flow',
                flow_step: '04-confirm-entry',
            },
            timeout: '5s',
        },
    );

    confirmDuration.add(confirmResponse.timings.duration);

    const confirmSuccess = isSuccessResponse(confirmResponse);

    if (!check(confirmResponse, {
        '04 드롭 입장 확정이 성공한다': () => confirmSuccess,
    })) {
        failFlow('04-confirm-entry', user, confirmResponse);
        return;
    }

    /*
     * 5. 재고 선점
     * 기존 drop-lock-concurrency.js와 동일하게 quantity를 전달합니다.
     */
    const lockResponse = http.post(
        `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/lock-start`,
        JSON.stringify({
            quantity: QUANTITY,
        }),
        {
            headers: {
                ...headers,
                'Content-Type': 'application/json',
            },
            tags: {
                api_name: 'drop-lock-start',
                test_type: 'drop-user-flow',
                flow_step: '05-lock-start',
            },
            timeout: '10s',
        },
    );

    lockDuration.add(lockResponse.timings.duration);

    const lockSuccess = isSuccessResponse(lockResponse);

    if (!check(lockResponse, {
        '05 드롭 재고 선점이 성공한다': () => lockSuccess,
    })) {
        failFlow('05-lock-start', user, lockResponse);
        return;
    }

    const elapsed = Date.now() - startedAt;

    totalDuration.add(elapsed);
    flowSuccess.add(1);

    console.log(
        [
            'DROP_USER_FLOW_SUCCESS',
            `memberId=${user.memberId}`,
            `dropId=${DROP_ID}`,
            `quantity=${QUANTITY}`,
            `duration=${elapsed}ms`,
        ].join(', '),
    );
}
