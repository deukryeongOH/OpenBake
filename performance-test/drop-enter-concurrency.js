import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { getUserForVu, users } from './k6-users.js';

const BASE_URL =
    __ENV.BASE_URL ?? 'http://localhost:8080';

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
 * 결과 측정용 Metric
 */
const enterSuccess =
    new Counter('drop_enter_success');

const enterConflict =
    new Counter('drop_enter_conflict');

const enterBadRequest =
    new Counter('drop_enter_bad_request');

const enterUnexpected =
    new Counter('drop_enter_unexpected');

const enterDuration =
    new Trend(
        'drop_enter_business_duration',
        true,
    );

/*
 * 기존 5명 테스트와 비교할 수 있도록
 * 기존 Threshold를 변경하지 않습니다.
 */
export const options = {
    scenarios: {
        concurrent_drop_enter: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: '30s',
        },
    },

    thresholds: {
        checks: [
            'rate==1',
        ],

        drop_enter_unexpected: [
            'count==0',
        ],

        drop_enter_business_duration: [
            'p(95)<1000',
            'p(99)<2000',
        ],
    },
};

export default function () {
    /*
     * 사용자 처리만 공통 모듈로 통일합니다.
     */
    const user = getUserForVu(__VU);

    const response = http.post(
        `${BASE_URL}/api/v1/drops/${DROP_ID}/enter`,
        null,
        {
            headers: {
                Accept: 'application/json',
                Authorization: `Bearer ${user.token}`,
            },

            tags: {
                api_name: 'drop-enter',
                test_type: 'drop-enter-concurrency',
            },

            timeout: '5s',
        },
    );

    enterDuration.add(response.timings.duration);

    if (response.status === 200) {
        enterSuccess.add(1);
    } else if (response.status === 409) {
        enterConflict.add(1);
    } else if (response.status === 400) {
        console.error(
            [
                'BAD_REQUEST',
                `memberId=${user.memberId}`,
                `status=${response.status}`,
                `body=${response.body}`,
            ].join(', '),
        );
        enterBadRequest.add(1);
    } else {
        enterUnexpected.add(1);

        console.error(
            [
                `memberId=${user.memberId}`,
                `status=${response.status}`,
                `body=${response.body}`,
            ].join(', '),
        );
    }

    check(response, {
        '신규 사용자의 대기열 진입이 성공한다':
            (res) =>
                res.status === 200,

        '응답 JSON의 success가 true이다':
            (res) => {
                if (res.status !== 200) {
                    return false;
                }

                try {
                    return res.json('success') === true;
                } catch {
                    return false;
                }
            },

        '응답 시간이 2초 미만이다':
            (res) =>
                res.timings.duration < 2000,
    });
}
