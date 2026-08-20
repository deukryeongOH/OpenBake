/*
 * OpenBake 성능테스트 인증 공통 모듈
 *
 * AUTH_MODE=direct
 *   - local Core(:8080)를 직접 호출
 *   - Gateway가 넣어주는 X-Openbake-* 헤더를 k6가 직접 생성
 *
 * AUTH_MODE=gateway
 *   - server/Nginx/API Gateway를 통해 호출
 *   - Member Service에서 발급받은 Bearer JWT 사용
 */

export function getAuthHeaders(user) {
    const authMode = (__ENV.AUTH_MODE ?? 'direct').toLowerCase();

    if (authMode === 'gateway') {
        if (!user.token) {
            throw new Error(
                `AUTH_MODE=gateway인데 memberId=${user.memberId}의 token이 없습니다.`
            );
        }

        return {
            Authorization: `Bearer ${user.token}`,
        };
    }

    if (authMode === 'direct') {
        if (user.memberId === undefined || user.memberId === null) {
            throw new Error('AUTH_MODE=direct인데 user.memberId가 없습니다.');
        }

        return {
            'X-Openbake-Member-Id': String(user.memberId),
            'X-Openbake-Member-Role': String(user.role ?? __ENV.TEST_MEMBER_ROLE ?? 'CUSTOMER'),
            'X-Openbake-Auth-Source': 'api-gateway',
        };
    }

    throw new Error(
        `지원하지 않는 AUTH_MODE=${authMode}. direct 또는 gateway를 사용하세요.`
    );
}
