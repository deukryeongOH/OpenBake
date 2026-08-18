import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const CORE_BASE_URL = __ENV.CORE_BASE_URL ?? 'http://localhost:8080';
const DROP_ID = __ENV.DROP_ID;
const AUTH_MODE = __ENV.AUTH_MODE ?? 'direct';
const TEST_MEMBER_ROLE = __ENV.TEST_MEMBER_ROLE ?? 'CUSTOMER';

const POLL_INTERVAL_MS = Number(__ENV.WAIT_ACTIVE_POLL_MS ?? 200);
const TIMEOUT_SECONDS = Number(__ENV.WAIT_ACTIVE_TIMEOUT_SECONDS ?? 30);

const users = new SharedArray('users', function () {
  return JSON.parse(open('./users.json'));
});

export const options = {
  scenarios: {
    wait_until_active: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.USER_COUNT ?? users.length),
      iterations: 1,
      maxDuration: `${TIMEOUT_SECONDS + 10}s`,
    },
  },
  thresholds: {
    checks: ['rate==1'],
    wait_active_success: [`count==${Number(__ENV.USER_COUNT ?? users.length)}`],
    wait_active_timeout: ['count==0'],
  },
};

function authHeaders(user) {
  if (AUTH_MODE === 'gateway') {
    return {
      Authorization: `Bearer ${user.token}`,
    };
  }

  return {
    'X-Openbake-Member-Id': String(user.memberId),
    'X-Openbake-Member-Role': user.role ?? TEST_MEMBER_ROLE,
    'X-Openbake-Auth-Source': 'api-gateway',
  };
}

function extractRank(response) {
  try {
    const body = response.json();
    if (body && body.data !== undefined && body.data !== null) {
      if (typeof body.data === 'number') {
        return Number(body.data);
      }

      // 응답이 객체인 경우 흔한 필드명을 순서대로 지원
      for (const key of ['rank', 'queueRank', 'waitingRank']) {
        if (body.data[key] !== undefined && body.data[key] !== null) {
          return Number(body.data[key]);
        }
      }
    }
  } catch (_) {
    // 아래 timeout/diagnostic 처리로 넘김
  }
  return null;
}

export default function () {
  const index = __VU - 1;
  const user = users[index];

  if (!user) {
    throw new Error(`users.json에 VU ${__VU}에 대응하는 사용자가 없습니다.`);
  }

  const deadline = Date.now() + TIMEOUT_SECONDS * 1000;
  let lastStatus = null;
  let lastBody = '';
  let lastRank = null;

  while (Date.now() < deadline) {
    const response = http.get(
      `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/rank`,
      {
        headers: {
          Accept: 'application/json',
          ...authHeaders(user),
        },
        tags: {
          api_name: 'drop-rank',
          test_type: 'wait-active',
        },
        timeout: '5s',
      },
    );

    lastStatus = response.status;
    lastBody = response.body;
    lastRank = extractRank(response);

    if (response.status === 200 && lastRank === 0) {
      wait_active_success.add(1);
      check(response, {
        '사용자가 ACTIVE 큐로 전환되었다': () => true,
      });
      return;
    }

    sleep(POLL_INTERVAL_MS / 1000);
  }

  wait_active_timeout.add(1);

  console.error(
    `WAIT_ACTIVE_TIMEOUT, memberId=${user.memberId}, ` +
    `status=${lastStatus}, rank=${lastRank}, body=${lastBody}`,
  );

  check(null, {
    '사용자가 제한시간 내 ACTIVE 큐로 전환되었다': () => false,
  });
}

import { Counter } from 'k6/metrics';

const wait_active_success = new Counter('wait_active_success');
const wait_active_timeout = new Counter('wait_active_timeout');
