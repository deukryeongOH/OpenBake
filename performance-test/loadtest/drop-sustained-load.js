import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';
import exec from 'k6/execution';
import { getAuthHeaders } from '../k6-auth.js';

/*
 * 지속 부하 시나리오 — 단일 인스턴스 한계 측정 / 오토스케일 관측용.
 *
 * ---------------------------------------------------------------------------
 * 왜 executor 를 ramping-arrival-rate 로 바꾸는가
 *
 * 기존 시나리오는 per-vu-iterations(VU 당 1회) 또는 ramping-vus 다. 둘 다
 * closed model 이라 "VU 는 이전 요청이 끝나야 다음 요청을 보낸다".
 * 서버가 느려지면 k6 가 스스로 요청 속도를 줄여버리므로 포화가 지표에 안 드러난다.
 * 실제로 100 VU 버스트에서 5.4초 만에 100건이 끝나 어떤 자원도 포화되지 않았다.
 *
 * ramping-arrival-rate 는 open model 이다. 서버 응답 속도와 무관하게 목표
 * 도착률(req/s)을 유지하려 시도하고, VU 가 모자라면 dropped_iterations 로 남긴다.
 * 실제 사용자는 서버가 느리다고 요청을 늦추지 않으므로 이쪽이 현실에 가깝다.
 *
 * ---------------------------------------------------------------------------
 * 왜 각 단계를 길게 유지하는가
 *
 * HPA 는 기본 15초마다 평가하고, backend Pod 는 startupProbe 유예가 240초다.
 * 단계를 30초씩 끊으면 HPA 가 판단하기도 전에 다음 단계로 넘어간다.
 * STEP_HOLD 기본값을 90초로 둔 이유이고, 오토스케일을 관측하려면 이보다 줄이면 안 된다.
 *
 * ---------------------------------------------------------------------------
 * TARGET
 *   confirm : POST /api/v1/drops/{id}/confirm-entry   (인증 필요, 기본값)
 *             DropEnterService.confirmEntry 는 이미 ENTERED 인 사용자에 대해
 *             쓰기 없이 반환하지만 drop 조회 -> 시간 검증 -> entry 조회 -> Redis peek 는
 *             매번 그대로 탄다. 게이트웨이 JWT 검증도 요청마다 걸린다.
 *             즉 상태를 바꾸지 않으면서 실제 경로 비용을 낸다. 반복 호출이 안전하다.
 *             사전조건: 대상 사용자가 이 드롭에 confirm 을 1회 마쳐 ENTERED 여야 한다.
 *
 *   info    : GET /api/v1/drops/{id}/info             (인증 불필요)
 *             users.json 도 JWT 도 필요 없다. 계정 준비 전에 인프라만 재볼 때.
 *
 * lock-start 는 재고를 소모해 반복 호출이 불가능하므로 이 시나리오에 쓸 수 없다.
 * ---------------------------------------------------------------------------
 */

const CORE_BASE_URL = __ENV.CORE_BASE_URL ?? 'http://localhost:8080';
const DROP_ID = Number(__ENV.DROP_ID ?? 0);
const TARGET = (__ENV.TARGET ?? 'confirm').toLowerCase();

/*
 * 부하 계획.
 *   START_RATE 에서 시작해 PEAK_RATE 까지 STEP_COUNT 단계로 올린다.
 *   각 단계는 STEP_HOLD 동안 유지한다.
 */
const START_RATE = Number(__ENV.START_RATE ?? 20);
const PEAK_RATE = Number(__ENV.PEAK_RATE ?? 100);
const STEP_COUNT = Number(__ENV.STEP_COUNT ?? 5);
const STEP_HOLD = __ENV.STEP_HOLD ?? '90s';
const STEP_RAMP = __ENV.STEP_RAMP ?? '15s';
const WARMUP_HOLD = __ENV.WARMUP_HOLD ?? '30s';

/*
 * VU 는 요청을 실어나르는 일꾼일 뿐 부하량을 정하지 않는다(도착률이 정한다).
 * 필요한 VU 수 ~= 도착률 x 응답시간. 100 req/s 에 3초면 300개가 필요하다.
 * 모자라면 dropped_iterations 가 늘어 실제 부하가 계획보다 낮아지므로 넉넉히 잡는다.
 */
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS ?? 100);
const MAX_VUS = Number(__ENV.MAX_VUS ?? 800);

const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT ?? '30s';
const SLOW_THRESHOLD_MS = Number(__ENV.SLOW_THRESHOLD_MS ?? 1500);

/*
 * users.json 은 confirm 모드에서만 필요하다.
 * info 모드는 계정 준비 없이 돌 수 있어야 하므로 없으면 빈 배열로 넘긴다.
 * (k6-users.js 를 그대로 import 하면 파일이 없을 때 init 단계에서 죽는다)
 */
const users = new SharedArray('sustained-users', function () {
    try {
        const parsed = JSON.parse(open('../users.json'));
        return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
        return [];
    }
});

if (!Number.isInteger(DROP_ID) || DROP_ID <= 0) {
    throw new Error(`DROP_ID는 1 이상의 정수여야 합니다. 현재값=${DROP_ID}`);
}
if (TARGET !== 'confirm' && TARGET !== 'info') {
    throw new Error(`TARGET은 confirm 또는 info만 지원합니다. 현재값=${TARGET}`);
}
if (TARGET === 'confirm' && users.length === 0) {
    throw new Error(
        'TARGET=confirm 은 users.json 이 필요합니다. ' +
        'loadtest/create-users.py 로 만들거나 TARGET=info 로 실행하세요.',
    );
}
if (PEAK_RATE < START_RATE) {
    throw new Error(`PEAK_RATE(${PEAK_RATE})는 START_RATE(${START_RATE}) 이상이어야 합니다.`);
}

/* 단계별 도착률. 리포트에서 "몇 req/s 에서 무너졌나"를 말하려면 이 값이 필요하다. */
function buildRatePlan() {
    const rates = [];
    if (STEP_COUNT <= 1) {
        rates.push(PEAK_RATE);
    } else {
        const gap = (PEAK_RATE - START_RATE) / (STEP_COUNT - 1);
        for (let i = 0; i < STEP_COUNT; i++) {
            rates.push(Math.round(START_RATE + gap * i));
        }
    }
    return rates;
}

const RATE_PLAN = buildRatePlan();

function buildStages() {
    // 첫 단계는 예열이라 램프 없이 바로 유지한다.
    const stages = [{ target: RATE_PLAN[0], duration: WARMUP_HOLD }];
    for (let i = 1; i < RATE_PLAN.length; i++) {
        stages.push({ target: RATE_PLAN[i], duration: STEP_RAMP });
        stages.push({ target: RATE_PLAN[i], duration: STEP_HOLD });
    }
    return stages;
}

/* 응답 분류 — "실패했다"보다 "어떻게 실패했다"가 중요하다. */
const respOk = new Counter('resp_ok');
const respGatewayTimeout = new Counter('resp_504');
const respBadGateway = new Counter('resp_502');
const respUnavailable = new Counter('resp_503');
const respConnError = new Counter('resp_conn_error');
const respOtherError = new Counter('resp_other_error');

const failureRate = new Rate('sustained_failure_rate');
const latency = new Trend('sustained_latency', true);

/*
 * "언제 무너졌나"를 숫자로 남긴다.
 * Trend 의 min 을 쓰면 최초 발생 시점의 도착률과 경과 시간이 그대로 보존된다.
 */
const slowAtRate = new Trend('slow_first_seen_at_rate');
const slowAtSec = new Trend('slow_first_seen_at_sec');
const errorAtRate = new Trend('error_first_seen_at_rate');
const errorAtSec = new Trend('error_first_seen_at_sec');

export const options = {
    scenarios: {
        sustained: {
            executor: 'ramping-arrival-rate',
            startRate: START_RATE,
            timeUnit: '1s',
            preAllocatedVUs: PRE_ALLOCATED_VUS,
            maxVUs: MAX_VUS,
            stages: buildStages(),
            gracefulStop: '30s',
        },
    },
    /*
     * 이 시나리오는 한계를 찾는 것이 목적이라 threshold 로 중단시키지 않는다.
     * NFR 초과 여부 기록용으로만 둔다(abortOnFail 없음).
     */
    thresholds: {
        sustained_latency: [{ threshold: `p(99)<3000`, abortOnFail: false }],
        sustained_failure_rate: [{ threshold: 'rate==0', abortOnFail: false }],
    },
};

/*
 * 경과 시간으로 현재 목표 도착률을 되짚는다.
 * k6 가 현재 stage target 을 직접 노출하지 않아 계획을 그대로 재계산한다.
 */
function parseDuration(text) {
    const match = String(text).match(/^(\d+(?:\.\d+)?)(ms|s|m|h)$/);
    if (!match) return 0;
    const value = Number(match[1]);
    const unit = match[2];
    if (unit === 'ms') return value / 1000;
    if (unit === 's') return value;
    if (unit === 'm') return value * 60;
    return value * 3600;
}

const STAGE_TIMELINE = (function () {
    const timeline = [];
    let elapsed = 0;
    for (const stage of buildStages()) {
        elapsed += parseDuration(stage.duration);
        timeline.push({ until: elapsed, rate: stage.target });
    }
    return timeline;
})();

function currentTargetRate(elapsedSec) {
    for (const point of STAGE_TIMELINE) {
        if (elapsedSec <= point.until) return point.rate;
    }
    return PEAK_RATE;
}

function userForVu(vu) {
    // 여러 VU 가 같은 사용자를 공유해도 된다. 이 경로는 상태를 바꾸지 않는다.
    return users[(vu - 1) % users.length];
}

function sendRequest() {
    if (TARGET === 'info') {
        return http.get(`${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/info`, {
            headers: { Accept: 'application/json' },
            tags: { api_name: 'drop-info', test_type: 'sustained-load' },
            timeout: HTTP_TIMEOUT,
        });
    }
    return http.post(
        `${CORE_BASE_URL}/api/v1/drops/${DROP_ID}/confirm-entry`,
        null,
        {
            headers: { Accept: 'application/json', ...getAuthHeaders(userForVu(__VU)) },
            tags: { api_name: 'drop-confirm-entry', test_type: 'sustained-load' },
            timeout: HTTP_TIMEOUT,
        },
    );
}

export default function () {
    const elapsedSec = exec.instance.currentTestRunDuration / 1000;
    const targetRate = currentTargetRate(elapsedSec);

    const res = sendRequest();
    latency.add(res.timings.duration);

    if (res.status === 200) {
        respOk.add(1);
        failureRate.add(false);
    } else {
        failureRate.add(true);
        errorAtRate.add(targetRate);
        errorAtSec.add(elapsedSec);

        if (res.status === 0) respConnError.add(1);
        else if (res.status === 504) respGatewayTimeout.add(1);
        else if (res.status === 503) respUnavailable.add(1);
        else if (res.status === 502) respBadGateway.add(1);
        else respOtherError.add(1);
    }

    if (res.timings.duration >= SLOW_THRESHOLD_MS) {
        slowAtRate.add(targetRate);
        slowAtSec.add(elapsedSec);
    }
}

export function handleSummary(data) {
    const count = (n) => (data.metrics[n] && data.metrics[n].values.count) || 0;
    const stat = (n, k) => (data.metrics[n] && data.metrics[n].values[k]) || 0;
    const rate = (n) => (data.metrics[n] && data.metrics[n].values.rate) || 0;
    const seen = (n) => Boolean(data.metrics[n] && data.metrics[n].values.count);

    /*
     * 총 요청 수를 Trend(sustained_latency)의 count 로 구하면 안 된다.
     * K6_SUMMARY_TREND_STATS 를 지정하는 순간 Trend 에는 거기 나열한 통계만 남고
     * count 가 빠져 0 이 된다. 그러면 failed = 0 - success 로 음수가 나온다.
     * 우리가 직접 세는 Counter 를 합치는 쪽이 설정과 무관하게 항상 정확하다.
     */
    const success = count('resp_ok');
    const failed = count('resp_504') + count('resp_503') + count('resp_502')
        + count('resp_conn_error') + count('resp_other_error');
    const total = success + failed;
    const dropped = count('dropped_iterations');
    const achieved = rate('http_reqs');

    const ms = (v) => `${v.toFixed(0)}ms`;
    const at = (rateMetric, secMetric) => {
        if (!seen(rateMetric)) return '발생하지 않음';
        return `${stat(rateMetric, 'min').toFixed(0)} req/s 구간 `
            + `(${stat(secMetric, 'min').toFixed(0)}초)`;
    };

    const L = [
        '',
        '==================================================================',
        ' 지속 부하 테스트',
        '==================================================================',
        `  대상        : ${TARGET === 'info'
            ? `GET /api/v1/drops/${DROP_ID}/info`
            : `POST /api/v1/drops/${DROP_ID}/confirm-entry`}`,
        `  부하 계획   : ${RATE_PLAN.join(' -> ')} req/s`,
        `                각 단계 ${STEP_HOLD} 유지 (램프 ${STEP_RAMP})`,
        '',
        '  ---- 무너진 지점 --------------------------------------------',
        `  ${SLOW_THRESHOLD_MS}ms 초과 최초 : ${at('slow_first_seen_at_rate', 'slow_first_seen_at_sec')}`,
        `  오류 최초 발생      : ${at('error_first_seen_at_rate', 'error_first_seen_at_sec')}`,
        '',
        '  ---- 처리량 --------------------------------------------------',
        `  목표 최대 도착률 : ${PEAK_RATE} req/s`,
        `  실제 평균 처리량 : ${achieved.toFixed(1)} req/s   (총 ${total}건)`,
        `  미발사(dropped)  : ${dropped}건`,
        dropped > 0
            ? '    <- VU 가 모자라 목표 도착률을 못 채운 구간이 있다.'
            : '    <- 계획한 부하를 전부 밀어넣었다.',
        '',
        '  ---- 응답 분류 ----------------------------------------------',
        `  성공(200)  : ${success}`,
        `  실패       : ${failed}  (${(rate('sustained_failure_rate') * 100).toFixed(2)}%)`,
        `    504 게이트웨이 타임아웃 : ${count('resp_504')}   <- 서버는 살아있으나 응답이 늦음`,
        `    503 서비스 불가         : ${count('resp_503')}   <- 스레드/큐 고갈`,
        `    502 백엔드 응답 불가    : ${count('resp_502')}   <- Pod 재시작·연결 끊김`,
        `    연결 실패(status=0)     : ${count('resp_conn_error')}   <- 연결 거부·리셋`,
        `    그 외                   : ${count('resp_other_error')}`,
        '',
        '  ---- 응답 시간 ----------------------------------------------',
        `  p50 ${ms(stat('sustained_latency', 'med'))}   `
        + `p95 ${ms(stat('sustained_latency', 'p(95)'))}   `
        + `p99 ${ms(stat('sustained_latency', 'p(99)'))}   `
        + `max ${ms(stat('sustained_latency', 'max'))}`,
        `  min ${ms(stat('sustained_latency', 'min'))}   `
        + `avg ${ms(stat('sustained_latency', 'avg'))}`,
        '',
        '==================================================================',
        '  해석',
        '    도착률은 오르는데 처리량이 평평   -> 포화. 그 지점이 인스턴스 한계다',
        '    dropped_iterations 증가          -> MAX_VUS 부족. 올리고 재측정할 것',
        '    504 / 연결실패 발생              -> 큐 한계 초과',
        '    Pod 가 늘어난 뒤 p99 회복        -> 오토스케일이 실제로 먹힘',
        '',
        '  서버 지표(스레드 풀·CPU·Hikari·replica)는 sample-backend.py 결과와',
        '  대조해야 판단이 선다. k6 만으로는 원인을 못 정한다.',
        '==================================================================',
        '',
    ];

    const NL = String.fromCharCode(10);
    return { stdout: L.join(NL) + NL };
}
