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

if (USER_COUNT > users.length) {
    throw new Error(
        `USER_COUNT=${USER_COUNT}, users.json=${users.length}. ` +
        `테스트 사용자 수가 부족합니다.`
    );
}

/*
 * 사용자 정의 메트릭
 */
const confirmSuccess =
    new Counter('confirm_entry_success');

const confirmUnauthorized =
    new Counter('confirm_entry_unauthorized');

const confirmDropNotFound =
    new Counter('confirm_entry_drop_not_found');

const confirmUnexpected =
    new Counter('confirm_entry_unexpected');

const confirmDuration =
    new Trend(
        'confirm_entry_business_duration',
        true,
    );

export const options = {
    scenarios: {
        concurrent_confirm_entry: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: '30s',
        },
    },

    thresholds: {
        // checks는 기능 정합성만 검증하고, 응답시간은 Trend threshold로 분리합니다.
        checks: ['rate==1'],

        http_req_failed: ['rate==0'],

        confirm_entry_success: [
            `count==${USER_COUNT}`,
        ],

        confirm_entry_unauthorized: ['count==0'],
        confirm_entry_drop_not_found: ['count==0'],
        confirm_entry_unexpected: ['count==0'],

        confirm_entry_business_duration: [
            'p(95)<1000',
            'p(99)<2000',
        ],
    },
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

export default function () {
    /*
     * enter와 동일하게 공통 사용자 매핑 사용
     */
    const user = getUserForVu(__VU);

    const response = http.post(
        `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/confirm-entry`,
        null,
        {
            headers: {
                Accept: 'application/json',
                ...getAuthHeaders(user),
            },

            tags: {
                api_name: 'drop-confirm-entry',
                test_type: 'confirm-entry-concurrency',
            },

            timeout: '5s',
        },
    );

    confirmDuration.add(response.timings.duration);

    const errorCode = extractErrorCode(response);
    const success = isSuccessResponse(response);

    /*
     * 기존 테스트의 오류 분류는 유지합니다.
     *
     * DR008: 드롭 진행 기간이 아님 (대기열 제거로 DR009 -> DR008)
     * DR001: 드롭 없음
     */
    const unauthorized =
        response.status === 400 &&
        errorCode === 'DR008';

    const dropNotFound =
        response.status === 404 &&
        errorCode === 'DR001';

    if (success) {
        confirmSuccess.add(1);
    } else if (unauthorized) {
        confirmUnauthorized.add(1);

        console.error(
            [
                'DROP_NOT_ACTIVE',
                `memberId=${user.memberId}`,
                `status=${response.status}`,
                `code=${errorCode}`,
                `body=${response.body}`,
            ].join(', '),
        );
    } else if (dropNotFound) {
        confirmDropNotFound.add(1);

        console.error(
            [
                'DROP_NOT_FOUND',
                `memberId=${user.memberId}`,
                `status=${response.status}`,
                `code=${errorCode}`,
                `body=${response.body}`,
            ].join(', '),
        );
    } else {
        confirmUnexpected.add(1);

        console.error(
            [
                'UNEXPECTED',
                `memberId=${user.memberId}`,
                `status=${response.status}`,
                `code=${errorCode}`,
                `duration=${response.timings.duration}ms`,
                `body=${response.body}`,
            ].join(', '),
        );
    }

    check(response, {
        '입장 확정 요청이 성공한다':
            () =>
                success,

        '응답 JSON의 success가 true이다':
            (res) => {
                try {
                    return res.json('success') === true;
                } catch {
                    return false;
                }
            },
    });
}
