#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
PERF_DIR = SCRIPT_DIR.parent
K6_ENV = PERF_DIR / ".env.k6"


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


def configure(profile: str) -> None:
    # 공통 → 프로파일 순서. 프로파일 파일이 공통값을 덮어쓴다.
    load_env_file(SCRIPT_DIR / ".env.perf")
    load_env_file(SCRIPT_DIR / f".env.{profile}", override=True)
    load_env_file(K6_ENV)

    os.environ["PERF_PROFILE"] = profile

    if profile == "local":
        os.environ.setdefault("CORE_BASE_URL", "http://localhost:8080")
        os.environ.setdefault("MEMBER_BASE_URL", "http://localhost:8081")
        os.environ.setdefault("AUTH_MODE", "direct")
        os.environ.setdefault("PERF_DB_CONTAINER", "openbake-postgres")
        os.environ.setdefault("PERF_BACKEND_CONTAINER", "openbake-backend")
        os.environ.setdefault("PERF_RESTART_BACKEND", "true")
    elif profile == "server":
        server = os.getenv("SERVER_BASE_URL", "").strip().rstrip("/")
        if not server:
            raise RuntimeError(
                "server 프로파일은 SERVER_BASE_URL이 필요합니다. "
                "performance-test/prepare/.env.server에 설정하세요."
            )
        # 서버는 nginx/api-gateway 하나의 공개 주소를 사용한다.
        os.environ["CORE_BASE_URL"] = server
        os.environ["MEMBER_BASE_URL"] = server
        os.environ.setdefault("AUTH_MODE", "gateway")
        os.environ.setdefault("PERF_DB_CONTAINER", "openbake-postgres")
        os.environ.setdefault("PERF_BACKEND_CONTAINER", "openbake-backend")
        os.environ.setdefault("PERF_RESTART_BACKEND", "true")
    else:
        raise RuntimeError("profile은 local 또는 server만 지원합니다.")


def env(name: str, default: str = "", required: bool = False) -> str:
    value = os.getenv(name, default).strip()
    if required and not value:
        raise RuntimeError(f"{name} 환경변수가 필요합니다.")
    return value


def api(
        method: str,
        path: str,
        *,
        service: str,
        auth: dict[str, Any] | None = None,
        body: dict[str, Any] | None = None,
        allow_status: set[int] | None = None,
) -> tuple[int, dict[str, Any] | None]:
    base_url = (
        env("MEMBER_BASE_URL", required=True)
        if service == "member"
        else env("CORE_BASE_URL", required=True)
    ).rstrip("/")
    url = f"{base_url}{path}"

    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")

    if auth:
        mode = env("AUTH_MODE", "direct")
        if mode == "gateway":
            headers["Authorization"] = f"Bearer {auth['accessToken']}"
        elif mode == "direct":
            # Core 직접 호출 시 Gateway가 주입하는 인증 헤더를 대신 넣는다.
            if service == "core":
                headers["X-Openbake-Member-Id"] = str(auth["memberId"])
                headers["X-Openbake-Member-Role"] = str(auth["role"])
                headers["X-Openbake-Auth-Source"] = "api-gateway"
            else:
                headers["Authorization"] = f"Bearer {auth['accessToken']}"
        else:
            raise RuntimeError(f"알 수 없는 AUTH_MODE={mode}")

    request = urllib.request.Request(url, data=data, method=method, headers=headers)

    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read().decode("utf-8", errors="replace")
            parsed = json.loads(raw) if raw.strip() else None
            return response.status, parsed
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw) if raw.strip() else None
        except json.JSONDecodeError:
            parsed = {"raw": raw}

        if allow_status and error.code in allow_status:
            return error.code, parsed

        raise RuntimeError(
            f"API 호출 실패: {method} {url}\n"
            f"status={error.code}\nbody={raw}"
        ) from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"서버 연결 실패: {url}\nreason={error.reason}") from error


def data_of(body: dict[str, Any] | None) -> Any:
    if not isinstance(body, dict) or "data" not in body:
        raise RuntimeError(f"예상하지 못한 API 응답입니다: {body}")
    return body["data"]


def login(email: str, password: str) -> dict[str, Any] | None:
    status, body = api(
        "POST",
        "/api/v1/auth/login",
        service="member",
        body={"email": email, "password": password},
        allow_status={400, 401, 404},
    )
    if status != 200:
        return None
    result = data_of(body)
    if not isinstance(result, dict) or not result.get("accessToken"):
        raise RuntimeError(f"로그인 응답 형식이 예상과 다릅니다: {body}")
    return result


def signup_if_needed() -> dict[str, Any]:
    email = env("PERF_SELLER_EMAIL", "perf-seller@openbake.test")
    password = env("PERF_SELLER_PASSWORD", "123456789")

    auth = login(email, password)
    if auth:
        print(f"[PERF] 판매자 테스트 회원 로그인 성공: {email}")
        return auth

    if env("PERF_ALLOW_SIGNUP", "true").lower() != "true":
        raise RuntimeError(
            f"테스트 판매자 로그인 실패: {email}\n"
            "PERF_ALLOW_SIGNUP=false 이므로 자동 회원가입하지 않습니다."
        )

    print(f"[PERF] 테스트 계정이 없어 회원가입: {email}")
    api(
        "POST",
        "/api/v1/auth/signup",
        service="member",
        body={
            "email": email,
            "password": password,
            "name": env("PERF_SELLER_NAME", "이세종"),
            "phoneNumber": env("PERF_SELLER_PHONE", "010-9999-9999"),
        },
        allow_status={409},
    )
    auth = login(email, password)
    if not auth:
        raise RuntimeError("회원가입 후 로그인에 실패했습니다.")
    return auth


def db_scalar(sql: str) -> str:
    if env("PERF_DB_ENABLED", "true").lower() != "true":
        raise RuntimeError(
            "이 작업은 DB 준비가 필요하지만 PERF_DB_ENABLED=false 입니다. "
            "서버 원격 실행이라면 서버 호스트에서 스크립트를 실행하거나 "
            "이미 APPROVED/ACTIVE 데이터를 준비하세요."
        )

    container = env("PERF_DB_CONTAINER", "openbake-postgres")
    db = env("PERF_DB_NAME", "openbake")
    user = env("PERF_DB_USER", "openbake")
    command = [
        "docker", "exec", container,
        "psql", "-U", user, "-d", db,
        "-At", "-c", sql,
    ]
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True)
    except FileNotFoundError as e:
        raise RuntimeError("docker 명령을 찾을 수 없습니다.") from e
    except subprocess.CalledProcessError as e:
        raise RuntimeError(
            "PostgreSQL 명령 실패\n"
            f"stdout={e.stdout}\nstderr={e.stderr}"
        ) from e
    return result.stdout.strip()


def get_my_seller(auth: dict[str, Any]) -> dict[str, Any] | None:
    status, body = api(
        "GET", "/api/v1/sellers/me",
        service="core", auth=auth,
        allow_status={404},
    )
    if status == 404:
        return None
    return data_of(body)


def create_seller_application(auth: dict[str, Any]) -> dict[str, Any]:
    print("[PERF] 계좌 mock 인증 요청")
    _, body = api(
        "POST",
        "/api/v1/sellers/settlement-account/verification-requests",
        service="core", auth=auth,
        body={
            "bankCode": env("PERF_BANK_CODE", "088"),
            "accountNumber": env("PERF_ACCOUNT_NUMBER", "110123456789"),
            "accountHolder": env("PERF_BUSINESS_REPRESENTATIVE", "이세종"),
        },
    )
    request_id = data_of(body)["verificationRequestId"]

    # mock-code endpoint는 local/dev에서만 사용할 수 있다.
    _, body = api(
        "GET",
        f"/api/v1/sellers/settlement-account/verification-requests/{request_id}/mock-code",
        service="core", auth=auth,
    )
    code = data_of(body)["code"]

    api(
        "POST",
        f"/api/v1/sellers/settlement-account/verification-requests/{request_id}/verify",
        service="core", auth=auth,
        body={"verificationCode": code},
    )

    print("[PERF] 판매자 입점 신청")
    _, body = api(
        "POST", "/api/v1/sellers/apply",
        service="core", auth=auth,
        body={
            "bakeryName": env("PERF_BAKERY_NAME", "OpenBake Performance Bakery"),
            "businessNumber": env("PERF_BUSINESS_NUMBER", "123-45-67890"),
            "businessAddress": env("PERF_BUSINESS_ADDRESS", "제주시 성능테스트로 1"),
            "businessRepresentativeName": env("PERF_BUSINESS_REPRESENTATIVE", "이세종"),
        },
    )
    return data_of(body)


def approve_seller(seller_id: int) -> None:
    # 성능테스트 서버에서 직접 실행하는 경우에도 운영 인증을 억지로 우회하지 않고
    # 테스트 fixture 준비에 한해 DB를 변경한다.
    print(f"[PERF] 테스트 판매자 APPROVED 처리: sellerId={seller_id}")
    result = db_scalar(
        "UPDATE sellers "
        "SET application_status='APPROVED', reject_reason=NULL, updated_at=NOW() "
        f"WHERE id={seller_id} RETURNING id;"
    )
    if not result:
        raise RuntimeError(f"sellerId={seller_id} 승인 대상을 찾지 못했습니다.")


def ensure_seller() -> tuple[dict[str, Any], dict[str, Any]]:
    auth = signup_if_needed()
    seller = get_my_seller(auth)

    if seller is None:
        if env("PERF_ALLOW_SELLER_APPLY", "true").lower() != "true":
            raise RuntimeError(
                "판매자 신청이 없습니다. PERF_ALLOW_SELLER_APPLY=false 입니다."
            )
        application = create_seller_application(auth)
        seller_id = int(application["sellerId"])
        approve_seller(seller_id)
        seller = get_my_seller(auth)

    if seller["applicationStatus"] != "APPROVED":
        approve_seller(int(seller["sellerId"]))
        seller = get_my_seller(auth)

    print(
        f"[PERF] 판매자 준비 완료: sellerId={seller['sellerId']}, "
        f"status={seller['applicationStatus']}"
    )
    return auth, seller


def find_free_registration_slot(seller_id: int) -> datetime:
    slots = [9, 11, 13, 15, 17]
    now = datetime.now()

    for offset in range(1, 61):
        day = (now + timedelta(days=offset)).date()
        seller_count = int(db_scalar(
            "SELECT COUNT(*) FROM drops d "
            "JOIN products p ON p.id=d.product_id "
            f"WHERE p.seller_id={seller_id} "
            f"AND d.drop_start::date='{day.isoformat()}';"
        ) or "0")
        if seller_count:
            continue

        day_count = int(db_scalar(
            "SELECT COUNT(*) FROM drops "
            f"WHERE drop_start::date='{day.isoformat()}';"
        ) or "0")
        if day_count >= 5:
            continue

        used = db_scalar(
            "SELECT COALESCE(string_agg(EXTRACT(HOUR FROM drop_start)::int::text, ','), '') "
            "FROM drops "
            f"WHERE drop_start::date='{day.isoformat()}';"
        )
        used_hours = {int(x) for x in used.split(",") if x.strip()}
        for hour in slots:
            if hour not in used_hours:
                return datetime.combine(day, datetime.min.time()).replace(hour=hour)

    raise RuntimeError("등록 가능한 Drop 슬롯을 찾지 못했습니다.")


def update_k6_env(values: dict[str, str]) -> None:
    lines = K6_ENV.read_text(encoding="utf-8").splitlines() if K6_ENV.exists() else []
    pending = dict(values)
    output: list[str] = []

    for line in lines:
        key = None
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in line:
            key = line.split("=", 1)[0].strip()
        if key in pending:
            output.append(f"{key}={pending.pop(key)}")
        else:
            output.append(line)

    if pending:
        output += ["", "# prepare-drop 자동 설정"]
        output += [f"{k}={v}" for k, v in pending.items()]

    K6_ENV.write_text("\n".join(output) + "\n", encoding="utf-8")


def restart_backend() -> None:
    if env("PERF_RESTART_BACKEND", "true").lower() != "true":
        return

    container = env("PERF_BACKEND_CONTAINER", "openbake-backend")
    print(f"[PERF] TodayDropCache 갱신: docker restart {container}")
    subprocess.run(["docker", "restart", container], check=True, stdout=subprocess.DEVNULL)

    deadline = time.time() + 120
    while time.time() < deadline:
        result = subprocess.run(
            [
                "docker", "inspect", "--format",
                "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}",
                container,
            ],
            capture_output=True, text=True,
        )
        status = result.stdout.strip()
        if status == "healthy":
            print("[PERF] backend healthy")
            return
        if status == "running":
            time.sleep(5)
            print("[PERF] backend running")
            return
        time.sleep(2)

    raise RuntimeError("backend가 정상 상태로 돌아오지 않았습니다.")


def create_drop(user_count: int) -> int:
    auth, seller = ensure_seller()
    seller_id = int(seller["sellerId"])
    start = find_free_registration_slot(seller_id)
    pickup = (start + timedelta(days=1)).date()

    quantity_per_user = int(env("QUANTITY", "1"))
    configured_stock = int(env("PERF_DROP_STOCK", "0") or "0")
    total = configured_stock or max(user_count * quantity_per_user, user_count)
    unique_name = f"PERF-DROP-{user_count}-{datetime.now():%Y%m%d%H%M%S}"

    print(f"[PERF] Drop 정상 API 등록: users={user_count}, stock={total}")
    api(
        "POST", "/api/v1/drops/register",
        service="core", auth=auth,
        body={
            "name": unique_name,
            "description": f"OpenBake performance test users={user_count}",
            "imageUrl": env("PERF_DROP_IMAGE_URL", "https://example.com/perf.jpg"),
            "pickUpAvailableDates": [pickup.isoformat()],
            "dropStart": start.strftime("%Y-%m-%dT%H:%M:%S"),
            "limitQuantity": int(env("PERF_DROP_LIMIT", str(max(quantity_per_user, 1)))),
            "price": int(env("PERF_DROP_PRICE", "10000")),
            "totalQuantity": total,
            "category": env("PERF_DROP_CATEGORY", "MEAL_BREADS"),
        },
    )

    escaped = unique_name.replace("'", "''")
    drop_id_raw = db_scalar(
        "SELECT d.id FROM drops d "
        "JOIN products p ON p.id=d.product_id "
        f"WHERE p.seller_id={seller_id} AND p.name='{escaped}' "
        "ORDER BY d.id DESC LIMIT 1;"
    )
    if not drop_id_raw:
        raise RuntimeError("생성된 dropId를 찾지 못했습니다.")
    drop_id = int(drop_id_raw)

    if env("PERF_ACTIVATE_NOW", "true").lower() == "true":
        # 성능테스트 직후 바로 enter를 실행할 수 있도록 현재 시간을 기준으로
        # 넉넉한 활성 구간을 강제로 만든다. DB의 NOW()를 사용하므로
        # WSL/호스트와 PostgreSQL 간 시스템 시간 차이에도 영향을 덜 받는다.
        timezone = env("PERF_TIMEZONE", "Asia/Seoul")

        db_scalar(
            "UPDATE drops SET "
            f"drop_start=(NOW() AT TIME ZONE '{timezone}') - INTERVAL '5 minutes', "
            f"drop_end=(NOW() AT TIME ZONE '{timezone}') + INTERVAL '2 hours', "
            "drop_status='ACTIVE' "
            f"WHERE id={drop_id} RETURNING id;"
        )

        active_window = db_scalar(
            "SELECT "
            "drop_start::text || ' | ' || "
            "drop_end::text || ' | now=' || "
            f"(NOW() AT TIME ZONE '{timezone}')::text || "
            "' | status=' || drop_status "
            f"FROM drops WHERE id={drop_id};"
        )
        print(f"[PERF] Drop 즉시 활성화 완료: {active_window}")
        restart_backend()

    # local은 직접 서비스 주소, server는 공개 gateway 주소가 두 값 모두에 들어간다.
    update_k6_env({
        "CORE_BASE_URL": env("CORE_BASE_URL", required=True),
        "MEMBER_BASE_URL": env("MEMBER_BASE_URL", required=True),
        "DROP_ID": str(drop_id),
        "USER_COUNT": str(user_count),
        "EXPECTED_SUCCESS": str(user_count),
        "EXPECTED_SOLD_OUT": "0",
    })

    print()
    print("========================================")
    print(f"profile       : {env('PERF_PROFILE')}")
    print(f"sellerId      : {seller_id}")
    print(f"dropId        : {drop_id}")
    print(f"userCount     : {user_count}")
    print(f"totalQuantity : {total}")
    print(f"coreUrl       : {env('CORE_BASE_URL')}")
    print(f"memberUrl     : {env('MEMBER_BASE_URL')}")
    print("========================================")
    return drop_id


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=["local", "server"])
    parser.add_argument("command", choices=["seller", "drop"])
    parser.add_argument("user_count", type=int, nargs="?")
    args = parser.parse_args()

    configure(args.profile)

    if args.command == "seller":
        ensure_seller()
    else:
        if args.user_count is None or args.user_count <= 0:
            raise RuntimeError("drop 명령에는 USER_COUNT가 필요합니다.")
        create_drop(args.user_count)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\nERROR: {e}", file=sys.stderr)
        sys.exit(1)
