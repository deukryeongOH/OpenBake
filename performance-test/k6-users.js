import { SharedArray } from 'k6/data';

/*
 * users.json은 모든 VU가 공유해서 한 번만 읽습니다.
 *
 * [
 *   {
 *     "memberId": 8,
 *     "email": "loadtest0001@naver.com",
 *     "token": "JWT..."
 *   }
 * ]
 */
export const users = new SharedArray(
    'openbake-load-test-users',
    function () {
        const data = JSON.parse(open('./users.json'));

        if (!Array.isArray(data) || data.length === 0) {
            throw new Error(
                'users.json이 비어 있거나 JSON 배열이 아닙니다.'
            );
        }

        data.forEach((user, index) => {
            if (user.memberId === undefined || user.memberId === null) {
                throw new Error(
                    `users.json[${index}]에 memberId가 없습니다.`
                );
            }

            if (!user.token) {
                throw new Error(
                    `users.json[${index}]에 JWT가 없습니다.`
                );
            }
        });

        return data;
    }
);

/*
 * __VU는 1부터 시작하고 JavaScript 배열은 0부터 시작합니다.
 *
 * VU 1   -> users[0]
 * VU 2   -> users[1]
 * ...
 */
export function getUserForVu(vuNumber) {
    const user = users[vuNumber - 1];

    if (!user) {
        throw new Error(
            `VU ${vuNumber}에 매핑할 사용자가 없습니다. ` +
            `users.json 사용자 수=${users.length}`
        );
    }

    return user;
}
