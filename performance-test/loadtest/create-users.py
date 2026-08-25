#!/usr/bin/env python3
"""
부하 테스트용 계정 생성 + JWT 발급.

기존 generate-user-json.py 는 이미 존재하는 계정에 로그인만 한다.
계정 자체를 만드는 단계가 없어 사용자 수를 늘릴 때마다 손으로 채워야 했다.
이 스크립트가 그 앞단까지 맡는다.

  1) 로그인 시도 -> 실패하면 회원가입 -> 다시 로그인
     (몇 번 돌려도 안전하다. 이미 있는 계정은 그냥 토큰만 새로 받는다)
  2) JWT payload 의 sub 에서 memberId 추출
  3) users-<N>.json 으로 저장

k6 스크립트(k6-users.js)는 항상 ./users.json 을 읽는다.
티어별 파일을 따로 두고 실행 직전에 users.json 으로 복사해서 쓴다(_common.sh 가 처리).

사용 예:
    python3 create-users.py --count 100
    python3 create-users.py --count 1000 --workers 4
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import threading
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
PERF_DIR = SCRIPT_DIR.parent
PREPARE_DIR = PERF_DIR / "prepare"

_print_lock = threading.Lock()


def load_env_file(path: Path, override: bool = False) -> None:
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if override or key not in os.environ:
            os.environ[key] = value


def env(name: str, default: str = "", required: bool = False) -> str:
    value = os.getenv(name, default).strip()
    if required and not value:
        raise RuntimeError(f"{name} 환경변수가 필요합니다.")
    return value


def request_json(
        url: str,
        *,
        method: str = "GET",
        body: dict[str, Any] | None = None,
        timeout: float = 20.0,
        allow_status: set[int] | None = None,
) -> tuple[int, dict[str, Any] | None]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")

    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            return resp.status, (json.loads(raw) if raw.strip() else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw) if raw.strip() else None
        except json.JSONDecodeError:
            parsed = {"raw": raw}
        if allow_status and e.code in allow_status:
            return e.code, parsed
        raise RuntimeError(f"{method} {url} status={e.code} body={raw}") from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"{url} 연결 실패: {e.reason}") from e


def member_id_from_jwt(token: str) -> int:
    """
    서명 검증이 아니다. payload 의 sub 를 읽어 테스트용 memberId 를 얻을 뿐이다.
    실제 인증은 서버가 한다.
    """
    parts = token.split(".")
    if len(parts) != 3:
        raise RuntimeError("accessToken 이 JWT 형식이 아닙니다.")
    payload = parts[1] + "=" * ((4 - len(parts[1]) % 4) % 4)
    claims = json.loads(base64.urlsafe_b64decode(payload).decode("utf-8"))
    sub = claims.get("sub")
    if sub is None:
        raise RuntimeError(f"JWT payload 에 sub 가 없습니다: {claims}")
    return int(sub)


def account_spec(index: int, prefix: str, domain: str) -> dict[str, str]:
    """
    전화번호에 유니크 제약이 걸려 있어 index 로 유일하게 만든다.
    실제 번호대와 겹치지 않도록 010-4xxx 대를 쓴다.
    """
    return {
        "email": f"{prefix}{index:04d}@{domain}",
        "name": f"부하테스트{index:04d}",
        "phoneNumber": f"010-{4000 + index // 10000:04d}-{index % 10000:04d}",
    }


def token_from(body: Any, token_path: str) -> str:
    node = body
    for key in token_path.split("."):
        if not isinstance(node, dict) or key not in node:
            raise RuntimeError(f"로그인 응답에서 '{token_path}' 를 찾지 못했습니다: {body}")
        node = node[key]
    return str(node)


def ensure_account(index: int, cfg: dict[str, Any]) -> dict[str, Any]:
    spec = account_spec(index, cfg["prefix"], cfg["domain"])
    email = spec["email"]
    password = cfg["password"]

    # 로그인을 먼저 시도한다. 있는 계정에 회원가입을 또 던지면
    # 중복 검사 실패가 로그를 덮어 진짜 문제를 못 보게 된다.
    status, body = request_json(
        cfg["login_url"], method="POST",
        body={cfg["email_field"]: email, cfg["password_field"]: password},
        timeout=cfg["timeout"], allow_status={400, 401, 403, 404},
    )

    created = False
    if status != 200:
        if not cfg["allow_signup"]:
            raise RuntimeError(f"{email} 로그인 실패(status={status}), PERF_ALLOW_SIGNUP=false")
        request_json(
            cfg["signup_url"], method="POST",
            body={"email": email, "password": password,
                  "name": spec["name"], "phoneNumber": spec["phoneNumber"]},
            timeout=cfg["timeout"], allow_status={409},
        )
        created = True
        status, body = request_json(
            cfg["login_url"], method="POST",
            body={cfg["email_field"]: email, cfg["password_field"]: password},
            timeout=cfg["timeout"],
        )
        if status != 200:
            raise RuntimeError(f"{email} 회원가입 후 로그인 실패(status={status})")

    token = token_from(body, cfg["token_path"])
    return {
        "memberId": member_id_from_jwt(token),
        "email": email,
        "token": token,
        "_created": created,
    }


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="부하 테스트 계정 생성 + JWT 발급")
    p.add_argument("--count", type=int, required=True)
    p.add_argument("--start-index", type=int, default=1)
    p.add_argument("--output", type=Path, default=None,
                   help="기본값 ../users-<count>.json")
    p.add_argument("--profile", default=os.getenv("PERF_PROFILE", "server"))
    p.add_argument("--workers", type=int,
                   default=int(os.getenv("ACCOUNT_WORKERS", "8")),
                   help="동시 요청 수. 회원가입은 비밀번호 해싱으로 CPU를 많이 쓴다. "
                        "member-service 를 죽이지 않게 낮게 유지할 것 (기본 8)")
    return p.parse_args()


def main() -> int:
    args = parse_args()

    load_env_file(PREPARE_DIR / ".env.perf")
    load_env_file(PREPARE_DIR / f".env.{args.profile}", override=True)
    load_env_file(PERF_DIR / f".env.k6.{args.profile}")

    # server 프로파일은 SERVER_BASE_URL 하나로 게이트웨이를 가리킨다.
    member_base = (env("MEMBER_BASE_URL") or env("SERVER_BASE_URL", required=True)).rstrip("/")

    login_path = env("LOGIN_PATH", "/api/v1/auth/login")
    signup_path = env("SIGNUP_PATH", "/api/v1/auth/signup")
    for name, path in (("LOGIN_PATH", login_path), ("SIGNUP_PATH", signup_path)):
        if not path.startswith("/"):
            raise RuntimeError(f"{name} 는 / 로 시작해야 합니다: {path}")

    cfg: dict[str, Any] = {
        "login_url": member_base + login_path,
        "signup_url": member_base + signup_path,
        "password": env("TEST_PASSWORD", "123456789"),
        "prefix": env("EMAIL_PREFIX", "loadtest"),
        "domain": env("EMAIL_DOMAIN", "naver.com"),
        "token_path": env("TOKEN_PATH", "data.accessToken"),
        "email_field": env("EMAIL_FIELD", "email"),
        "password_field": env("PASSWORD_FIELD", "password"),
        "timeout": float(env("REQUEST_TIMEOUT", "20")),
        "allow_signup": env("PERF_ALLOW_SIGNUP", "true").lower() == "true",
    }

    output = args.output or (PERF_DIR / f"users-{args.count}.json")
    last = args.start_index + args.count - 1

    print("=" * 52)
    print(f"member   : {member_base}")
    print(f"계정     : {args.count}명 "
          f"({cfg['prefix']}{args.start_index:04d} ~ {cfg['prefix']}{last:04d}@{cfg['domain']})")
    print(f"동시성   : {args.workers}")
    print(f"출력     : {output}")
    print("=" * 52)

    results: dict[int, dict[str, Any]] = {}
    failures: list[str] = []
    done = 0

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {
            pool.submit(ensure_account, i, cfg): i
            for i in range(args.start_index, args.start_index + args.count)
        }
        for future in as_completed(futures):
            index = futures[future]
            done += 1
            try:
                results[index] = future.result()
            except Exception as exc:  # 한 계정 실패로 전체를 버리지 않는다
                failures.append(f"index={index}: {exc}")
            with _print_lock:
                if done % 50 == 0 or done == args.count:
                    print(f"  진행 {done}/{args.count}   실패 {len(failures)}")

    if not results:
        print("\nERROR: 성공한 계정이 없습니다.", file=sys.stderr)
        for line in failures[:5]:
            print(f"  {line}", file=sys.stderr)
        return 1

    # VU 번호(__VU)와 사용자를 안정적으로 대응시켜야 하므로 순서를 고정한다.
    ordered = [results[i] for i in sorted(results)]
    created = sum(1 for u in ordered if u.pop("_created", False))

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(ordered, ensure_ascii=False, indent=2) + "\n",
                      encoding="utf-8")

    print()
    print(f"완료: {len(ordered)}명 (신규 {created} / 기존 {len(ordered) - created})")
    print(f"저장: {output}")

    if failures:
        print(f"\nWARN: {len(failures)}건 실패 (앞 5건)")
        for line in failures[:5]:
            print(f"  {line}")
        print("\n부하 테스트는 성공한 계정 수까지만 돌릴 수 있습니다.")
        print("실패가 많으면 --workers 를 낮춰 다시 실행하세요(member-service 과부하).")
        return 1 if len(ordered) < args.count else 0

    return 0


if __name__ == "__main__":
    raise SystemExit(main())