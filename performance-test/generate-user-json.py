#!/usr/bin/env python3
from __future__ import annotations

import base64
import json
import os
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


def env(
        name: str,
        default: str | None = None,
        required: bool = False,
) -> str:
    value = os.getenv(name, default) or ""
    value = value.strip()

    if required and not value:
        raise RuntimeError(
            f"{name} 환경변수가 필요합니다."
        )

    return value


def get_by_path(
        data: Any,
        path: str,
) -> Any:
    current = data

    for key in path.split("."):
        if (
                not isinstance(current, dict) or
                key not in current
        ):
            raise KeyError(
                f"응답에서 '{path}' 경로를 찾지 못했습니다."
            )

        current = current[key]

    return current


def decode_jwt_payload(
        token: str,
) -> dict[str, Any]:
    """
    서버에서 발급받은 JWT payload를 읽어
    테스트용 memberId(sub)를 가져옵니다.

    서명 검증을 대신하는 함수가 아닙니다.
    실제 인증은 서버에서 수행합니다.
    """
    parts = token.split(".")

    if len(parts) != 3:
        raise RuntimeError(
            "발급받은 문자열이 JWT 형식(header.payload.signature)이 아닙니다."
        )

    payload_part = parts[1]

    padding = "=" * (
            (4 - len(payload_part) % 4) % 4
    )

    try:
        decoded = base64.urlsafe_b64decode(
            payload_part + padding
        )
        payload = json.loads(
            decoded.decode("utf-8")
        )
    except Exception as error:
        raise RuntimeError(
            "JWT payload를 해석하지 못했습니다."
        ) from error

    if not isinstance(payload, dict):
        raise RuntimeError(
            "JWT payload가 JSON 객체가 아닙니다."
        )

    return payload


def member_id_from_jwt(
        token: str,
) -> int | str:
    payload = decode_jwt_payload(token)

    subject = payload.get("sub")

    if subject is None:
        raise RuntimeError(
            "JWT payload에 sub가 없습니다."
        )

    subject_text = str(subject)

    if subject_text.isdigit():
        return int(subject_text)

    return subject_text


def atomic_write_json(
        path: Path,
        data: Any,
) -> None:
    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
    ) as temp:
        json.dump(
            data,
            temp,
            ensure_ascii=False,
            indent=2,
        )
        temp.write("\n")

        temp_path = Path(temp.name)

    temp_path.replace(path)


def make_accounts() -> list[dict[str, str]]:
    user_count = int(
        env("USER_COUNT", "100")
    )

    start_index = int(
        env("START_INDEX", "1")
    )

    email_prefix = env(
        "EMAIL_PREFIX",
        "loadtest",
    )

    email_domain = env(
        "EMAIL_DOMAIN",
        "naver.com",
    )

    if user_count <= 0:
        raise RuntimeError(
            "USER_COUNT는 1 이상이어야 합니다."
        )

    return [
        {
            "email":
                f"{email_prefix}{index:04d}@{email_domain}"
        }
        for index in range(
            start_index,
            start_index + user_count,
            )
    ]


def login(
        *,
        url: str,
        email: str,
        password: str,
        email_field: str,
        password_field: str,
        token_path: str,
        timeout: float,
) -> str:
    payload = json.dumps({
        email_field: email,
        password_field: password,
    }).encode("utf-8")

    request = urllib.request.Request(
        url,
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )

    try:
        with urllib.request.urlopen(
                request,
                timeout=timeout,
        ) as response:
            status = response.status
            raw = response.read().decode(
                "utf-8"
            )

    except urllib.error.HTTPError as error:
        body = error.read().decode(
            "utf-8",
            errors="replace",
        )

        raise RuntimeError(
            f"로그인 실패: "
            f"email={email}, "
            f"status={error.code}, "
            f"body={body}"
        ) from error

    except urllib.error.URLError as error:
        raise RuntimeError(
            f"서버 연결 실패: "
            f"email={email}, "
            f"reason={error.reason}"
        ) from error

    if not 200 <= status < 300:
        raise RuntimeError(
            f"로그인 실패: "
            f"email={email}, "
            f"status={status}, "
            f"body={raw}"
        )

    try:
        body = json.loads(raw)

    except json.JSONDecodeError as error:
        raise RuntimeError(
            f"로그인 응답이 JSON이 아닙니다: "
            f"email={email}, "
            f"body={raw}"
        ) from error

    token = get_by_path(
        body,
        token_path,
    )

    if (
            not isinstance(token, str) or
            not token.strip()
    ):
        raise RuntimeError(
            f"{email}: "
            f"TOKEN_PATH={token_path} 값이 "
            f"유효한 JWT 문자열이 아닙니다."
        )

    return token


def main() -> int:
    member_base_url = env(
        "MEMBER_BASE_URL",
        required=True,
    ).rstrip("/")

    login_path = env(
        "LOGIN_PATH",
        required=True,
    )

    password = env(
        "TEST_PASSWORD",
        required=True,
    )

    if not login_path.startswith("/"):
        login_path = "/" + login_path

    login_url = (
            member_base_url +
            login_path
    )

    token_path = env(
        "TOKEN_PATH",
        "data.accessToken",
    )

    email_field = env(
        "EMAIL_FIELD",
        "email",
    )

    password_field = env(
        "PASSWORD_FIELD",
        "password",
    )

    output_file = Path(
        env(
            "OUTPUT_FILE",
            "users.json",
        )
    )

    timeout = float(
        env(
            "REQUEST_TIMEOUT",
            "10",
        )
    )

    accounts = make_accounts()

    users: list[
        dict[str, Any]
    ] = []

    print(
        f"로그인 URL     : {login_url}"
    )
    print(
        f"대상 사용자 수  : {len(accounts)}"
    )
    print(
        f"출력 파일      : {output_file}"
    )
    print()

    for index, account in enumerate(
            accounts,
            start=1,
    ):
        email = account["email"]

        token = login(
            url=login_url,
            email=email,
            password=password,
            email_field=email_field,
            password_field=password_field,
            token_path=token_path,
            timeout=timeout,
        )

        # JWT의 sub 값을 memberId로 사용합니다.
        member_id = member_id_from_jwt(
            token
        )

        users.append({
            "memberId": member_id,
            "email": email,
            "token": token,
        })

        print(
            f"[{index:03d}/{len(accounts):03d}] "
            f"OK memberId={member_id}, "
            f"email={email}"
        )

    atomic_write_json(
        output_file,
        users,
    )

    print()
    print(
        f"완료: {output_file} "
        f"({len(users)} users)"
    )
    print(
        "주의: users.json은 "
        "Git에 커밋하지 마세요."
    )

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(
            main()
        )

    except (
            RuntimeError,
            ValueError,
            KeyError,
    ) as error:
        print(
            f"ERROR: {error}",
            file=sys.stderr,
        )
        raise SystemExit(1)
