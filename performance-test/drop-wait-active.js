import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';

const CORE_BASE_URL = __ENV.CORE_BASE_URL ?? 'http://localhost:8080';
const DROP_ID = __ENV.DROP_ID;
const AUTH_MODE = __ENV.AUTH_MODE ?? 'direct';
const TEST_MEMBER_ROLE = __ENV.TEST_MEMBER_ROLE ?? 'CUSTOMER';

const POLL_INTERVAL_MS = Number(__ENV.WAIT_ACTIVE_POLL_MS ?? 200);
const TIMEOUT_SECONDS = Number(__ENV.WAIT_ACTIVE_TIMEOUT_SECONDS ?? 30);
const USER_COUNT = Number(__ENV.USER_COUNT ?? 100);

const waitActiveSuccess = new Counter('wait_active_success');
const waitActiveTimeout = new Counter('wait_active_timeout');
const waitActiveUnexpected = new Counter('wait_active_unexpected');

const users = new SharedArray('users', function () {
  return JSON.parse(open('./users.json'));
});

export const options = {
  scenarios: {
    wait_until_active: {
      executor: 'per-vu-iterations',
      vus: USER_COUNT,
      iterations: 1,
      maxDuration: `${TIMEOUT_SECONDS + 10}s`,
    },
  },
  thresholds: {
    checks: ['rate==1'],
    wait_active_success: [`count==${USER_COUNT}`],
    wait_active_timeout: ['count==0'],
    wait_active_unexpected: ['count==0'],
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

function parseQueue(response) {
  try {
    const body = response.json();
    const data = body?.data;

    return {
      success: body?.success === true,
      rank: data?.rank !== undefined && data?.rank !== null
        ? Number(data.rank)
        : null,
      status: data?.status ?? null,
      code: body?.error?.code ?? null,
    };
  } catch (_) {
    return {
      success: false,
      rank: null,
      status: null,
      code: null,
    };
  }
}

export default function () {
  const user = users[__VU - 1];

  if (!user) {
    throw new Error(`users.json에 VU ${__VU}에 대응하는 사용자가 없습니다.`);
  }

  const deadline = Date.now() + TIMEOUT_SECONDS * 1000;

  let lastStatus = null;
  let lastRank = null;
  let lastQueueStatus = null;
  let lastBody = '';

  while (Date.now() < deadline) {
    // 최신 OpenBake 코드의 실제 API:
    // GET /api/v1/drops/{dropId}/queue/rank
    const response = http.get(
      `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/queue/rank`,
      {
        headers: {
          Accept: 'application/json',
          ...authHeaders(user),
        },
        tags: {
          api_name: 'drop-queue-rank',
          test_type: 'wait-active',
        },
        timeout: '5s',
      },
    );

    const queue = parseQueue(response);

    lastStatus = response.status;
    lastRank = queue.rank;
    lastQueueStatus = queue.status;
    lastBody = response.body;

    // InMemoryQueueManager:
    // ACTIVE -> rank == 0
    if (response.status === 200 && queue.success && queue.rank === 0) {
      waitActiveSuccess.add(1);

      check(response, {
        '사용자가 ACTIVE 큐로 전환되었다': () => true,
      });
      return;
    }

    // 인증/라우팅/비즈니스 오류는 30초 동안 무의미하게 polling하지 않고 즉시 실패
    if (response.status !== 200) {
      waitActiveUnexpected.add(1);

      console.error(
        `WAIT_ACTIVE_ERROR, memberId=${user.memberId}, ` +
        `status=${response.status}, code=${queue.code}, body=${response.body}`,
      );

      check(response, {
        '대기열 순번 조회가 정상 응답한다': () => false,
      });
      return;
    }

    sleep(POLL_INTERVAL_MS / 1000);
  }

  waitActiveTimeout.add(1);

  console.error(
    `WAIT_ACTIVE_TIMEOUT, memberId=${user.memberId}, ` +
    `status=${lastStatus}, rank=${lastRank}, queueStatus=${lastQueueStatus}, ` +
    `body=${lastBody}`,
  );

  check(null, {
    '사용자가 제한시간 내 ACTIVE 큐로 전환되었다': () => false,
  });
}
