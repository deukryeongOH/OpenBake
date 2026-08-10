BEGIN;

-- 기존 테스트 계정 중복 확인
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM auths
        WHERE email ~ '^loadtest[0-9]{4}@naver\.com$'
    ) THEN
        RAISE EXCEPTION
            'loadtest 계정이 이미 존재합니다. 먼저 기존 데이터를 정리하세요.';
END IF;
END
$$;

WITH test_users AS (
    SELECT
        number,
        'loadtest' || LPAD(number::TEXT, 4, '0') AS member_name,
        '010' || LPAD((90000000 + number)::TEXT, 8, '0')
                                                 AS phone_number
    FROM generate_series(1, 100) AS number
),
     created_members AS (
INSERT INTO members (
    name,
    phone_number,
    role,
    status
)
SELECT
    member_name,
    phone_number,
    'CUSTOMER',
    'ACTIVE'
FROM test_users
         RETURNING
    id,
        name
),
numbered_members AS (
SELECT
    id AS member_id,
    name,
    ROW_NUMBER() OVER (ORDER BY id) AS row_number
FROM created_members
    ),
    auth_id_base AS (
SELECT COALESCE(MAX(id), 0) AS max_id
FROM auths
    )
INSERT INTO auths (
    id,
    email,
    member_id,
    password_hash,
    provider,
    provider_id
)
SELECT
    auth_id_base.max_id + numbered_members.row_number,
    numbered_members.name || '@naver.com',
    numbered_members.member_id,
    '$2a$10$l8boesww3sf8WIbhQn8LbeaJeDNZM1gFZ3SKlTepgSRS7rCOJWLIW',
    'LOCAL',
    NULL
FROM numbered_members
         CROSS JOIN auth_id_base;

COMMIT;

SELECT COUNT(*) AS created_user_count
FROM auths
WHERE email ~ '^loadtest[0-9]{4}@naver\.com$';
SELECT
    a.id AS auth_id,
    a.email,
    a.member_id,
    m.id AS member_id_check,
    m.name,
    m.phone_number,
    m.role,
    m.status
FROM auths a
         JOIN members m
              ON m.id = a.member_id
WHERE a.email ~ '^loadtest[0-9]{4}@naver\.com$'
ORDER BY a.email;
SELECT
    a.id,
    a.email,
    a.member_id
FROM auths a
         LEFT JOIN members m
                   ON m.id = a.member_id
WHERE a.email ~ '^loadtest[0-9]{4}@naver\.com$'
  AND m.id IS NULL;

SELECT jsonb_pretty(
               jsonb_agg(
                       jsonb_build_object(
                               'memberId', m.id,
                               'email', a.email
                       )
                           ORDER BY a.email
               )
       ) AS member_map
FROM auths a
         JOIN members m
              ON m.id = a.member_id
WHERE a.email ~ '^loadtest[0-9]{4}@naver\.com$';
