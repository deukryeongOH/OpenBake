#!/usr/bin/env bash
# Node A에서 실행한다. Secret 값 자체는 절대 출력하지 않는다.
#
#   ./apply-secrets.sh scaffold   /opt/openbake/secrets/*.env 틀 생성 (기존 파일은 건드리지 않음)
#   ./apply-secrets.sh apply      env file을 읽어 Secret 생성·갱신
#   ./apply-secrets.sh verify     Secret 이름과 key만 확인
#
# registry-credentials는 dockerconfigjson이라 여기서 다루지 않는다. README 참고.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRET_DIR="${SECRET_DIR:-/opt/openbake/secrets}"

# secret 이름:env 파일명:namespace
SECRETS=(
    "core-db-credentials:core-db.env:openbake"
    "member-db-credentials:member-db.env:openbake"
    "payment-db-credentials:payment-db.env:openbake"
    "ai-db-credentials:ai-db.env:openbake"
    "jwt-credentials:jwt.env:openbake"
    "toss-credentials:toss.env:openbake"
    "settlement-credentials:settlement.env:openbake"
    "ai-provider-credentials:ai-provider.env:openbake"
    "service-auth-credentials:service-auth.env:openbake"
    "s3-credentials:s3.env:openbake"
    "grafana-admin-credentials:grafana-admin.env:monitoring"
)

# 값 출처 안내. scaffold가 각 파일 상단에 주석으로 넣는다.
source_hint() {
    case "$1" in
        core-db-credentials|member-db-credentials|payment-db-credentials|ai-db-credentials)
            echo "k3s PostgreSQL이 이 값으로 초기화된다. restore는 --no-owner라 Compose와 달라도 되지만 같게 두면 혼란이 적다." ;;
        jwt-credentials)
            echo "새로 생성한다 (openssl rand -base64 48). 12번 문서 6.1장: cutover 전에 k3s Secret에만 준비하고 Compose에 반영하지 않는다." ;;
        service-auth-credentials)
            echo "내부 서비스 간 인증 토큰. 새로 생성해도 된다 (openssl rand -base64 32)." ;;
        settlement-credentials)
            echo "*** Compose와 반드시 동일해야 한다. restore된 정산 데이터를 복호화하는 key다. 회전 금지. ***" ;;
        toss-credentials)
            echo "Compose와 동일. 같은 Toss 계정을 쓴다." ;;
        ai-provider-credentials)
            echo "Compose와 동일. 같은 OpenAI 계정을 쓴다." ;;
        s3-credentials)
            echo "Compose와 동일. 같은 버킷을 쓴다." ;;
        grafana-admin-credentials)
            echo "새로 정한다. Grafana 최초 admin 계정." ;;
    esac
}

cmd_scaffold() {
    mkdir -p "$SECRET_DIR"
    chmod 700 "$SECRET_DIR"

    local created=0 skipped=0
    for entry in "${SECRETS[@]}"; do
        IFS=: read -r name file _ns <<< "$entry"
        local target="$SECRET_DIR/$file"
        local example="$SCRIPT_DIR/${name}.env.example"

        if [[ ! -f "$example" ]]; then
            echo "WARN: 템플릿이 없다: $example" >&2
            continue
        fi
        if [[ -f "$target" ]]; then
            echo "SKIP  $file (이미 존재 — 덮어쓰지 않는다)"
            skipped=$((skipped + 1))
            continue
        fi

        {
            echo "# $name"
            echo "# $(source_hint "$name")"
            echo "# 값을 채운 뒤 './apply-secrets.sh apply'를 실행한다."
            cat "$example"
        } > "$target"
        chmod 600 "$target"
        echo "CREATE $target"
        created=$((created + 1))
    done

    echo
    echo "생성 $created / 건너뜀 $skipped"
    echo "각 파일을 'sudoedit $SECRET_DIR/<파일>'로 열어 값을 채운다."
}

# 값이 비어 있는 key가 있으면 이름만 알려준다. 값은 출력하지 않는다.
check_blank() {
    local file="$1"
    awk -F= '
        /^[[:space:]]*#/ { next }
        /^[[:space:]]*$/ { next }
        NF < 2 || $2 == "" { print $1 }
    ' "$file"
}

cmd_apply() {
    local missing=0

    for entry in "${SECRETS[@]}"; do
        IFS=: read -r name file _ns <<< "$entry"
        local target="$SECRET_DIR/$file"
        if [[ ! -f "$target" ]]; then
            echo "MISSING $file"
            missing=$((missing + 1))
            continue
        fi
        local blanks
        blanks="$(check_blank "$target" | paste -sd, -)"
        if [[ -n "$blanks" ]]; then
            echo "BLANK   $file — 비어 있는 key: $blanks"
            missing=$((missing + 1))
        fi
    done

    if (( missing > 0 )); then
        echo
        echo "ERROR: 준비되지 않은 파일이 ${missing}개다. 값을 채운 뒤 다시 실행한다." >&2
        exit 1
    fi

    echo "==> 모든 env file 준비 확인. 적용을 시작한다."
    echo

    for entry in "${SECRETS[@]}"; do
        IFS=: read -r name file ns <<< "$entry"
        kubectl -n "$ns" create secret generic "$name" \
            --from-env-file="$SECRET_DIR/$file" \
            --dry-run=client -o yaml | kubectl apply -f - >/dev/null
        echo "OK  $ns/$name"
    done

    echo
    echo "registry-credentials(GHCR)는 별도 절차다. README.md 참고."
}

cmd_verify() {
    for ns in openbake monitoring; do
        echo "=== namespace: $ns"
        for entry in "${SECRETS[@]}"; do
            IFS=: read -r name _file entry_ns <<< "$entry"
            [[ "$entry_ns" == "$ns" ]] || continue
            if keys="$(kubectl -n "$ns" get secret "$name" -o json 2>/dev/null \
                        | python3 -c 'import json,sys; print(",".join(sorted(json.load(sys.stdin).get("data",{}))))')"; then
                echo "  OK      $name  [$keys]"
            else
                echo "  MISSING $name"
            fi
        done
    done

    echo
    echo "=== registry-credentials"
    if kubectl -n openbake get secret registry-credentials >/dev/null 2>&1; then
        echo "  OK"
    else
        echo "  MISSING — README.md의 docker-registry 절차로 별도 생성"
    fi
}

case "${1:-}" in
    scaffold) cmd_scaffold ;;
    apply)    cmd_apply ;;
    verify)   cmd_verify ;;
    *)
        echo "사용법: $0 {scaffold|apply|verify}" >&2
        exit 1
        ;;
esac
