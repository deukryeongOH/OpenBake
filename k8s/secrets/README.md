# k8s/secrets/

이 디렉터리에는 실제 Secret 값을 두지 않는다. `*.env.example`은 필요한 key 이름만 보여주는 placeholder다.

실제 값은 Node A의 repository 밖 경로(`/opt/openbake/secrets/`, 권한 `0600`)에 운영자가 직접 만든다.

## `openbake` Namespace Secret

| Secret | env file | Key | 사용 워크로드 |
| --- | --- | --- | --- |
| `core-db-credentials` | `core-db.env` | `DB_USERNAME`, `DB_PASSWORD` | backend, core-postgres |
| `member-db-credentials` | `member-db.env` | `DB_USERNAME`, `DB_PASSWORD` | member-service, member-postgres |
| `payment-db-credentials` | `payment-db.env` | `DB_USERNAME`, `DB_PASSWORD` | payment-service, payment-postgres |
| `ai-db-credentials` | `ai-db.env` | `DB_USERNAME`, `DB_PASSWORD` | ai-service, ai-postgres |
| `jwt-credentials` | `jwt.env` | `JWT_SECRET` | api-gateway, member-service |
| `toss-credentials` | `toss.env` | `TOSS_SECRET_KEY` | backend, payment-service |
| `settlement-credentials` | `settlement.env` | `SETTLEMENT_ENCRYPTION_KEY` | backend만 |
| `ai-provider-credentials` | `ai-provider.env` | `OPENAI_API_KEY` | ai-service |
| `service-auth-credentials` | `service-auth.env` | `AI_SERVICE_TOKEN`, `CORE_SERVICE_TOKEN` | backend, ai-service |
| `s3-credentials` | `s3.env` | `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` | backend |
| `registry-credentials` | (dockerconfigjson, 아래 별도 절차) | — | GHCR image pull |

`ai-db-credentials`, `ai-provider-credentials`, `service-auth-credentials`, `s3-credentials`는 08번 설계 문서(2026-08-18 작성) 이후 추가된 ai-service·S3 연동에 대응하기 위해 이번 이슈에서 신설했다. 근거는 저장소 루트 `docs/k3s-learning/issue-2-implementation-prompt.md` 보고서 참고.

`jwt-credentials`는 backend에 전달하지 않는다. backend는 Gateway가 붙인 신원 header만 신뢰한다.

## `monitoring` Namespace Secret

| Secret | env file | Key | 사용 워크로드 |
| --- | --- | --- | --- |
| `grafana-admin-credentials` | `grafana-admin.env` | `GF_SECURITY_ADMIN_USER`, `GF_SECURITY_ADMIN_PASSWORD` | Grafana |

## 생성 명령

Node A에서 `/opt/openbake/secrets/`에 실제 값을 담은 env file을 만든 뒤 적용한다. 값이 shell history에 남지 않도록 `--from-env-file`을 사용한다.

```bash
sudo mkdir -p /opt/openbake/secrets
sudo chmod 700 /opt/openbake/secrets

# 예: core-db-credentials
sudoedit /opt/openbake/secrets/core-db.env   # DB_USERNAME=..., DB_PASSWORD=... 실제 값 입력
sudo chmod 600 /opt/openbake/secrets/core-db.env

kubectl -n openbake create secret generic core-db-credentials \
  --from-env-file=/opt/openbake/secrets/core-db.env \
  --dry-run=client -o yaml | kubectl apply -f -
```

나머지 Secret도 같은 방식으로 반복한다. `grafana-admin-credentials`만 `-n monitoring`을 쓴다.

### `registry-credentials` (GHCR pull)

dockerconfigjson 형식이라 env file이 아니라 전용 명령을 쓴다. `read:packages`만 가진 전용 PAT를 사용하고 CI의 `GITHUB_TOKEN`을 저장하지 않는다.

```bash
kubectl -n openbake create secret docker-registry registry-credentials \
  --docker-server=ghcr.io \
  --docker-username=<github-username> \
  --docker-password=<read:packages 전용 PAT> \
  --docker-email=<email>
```

## 값을 출력하지 않고 존재만 확인하는 방법

Secret 값을 `kubectl exec ... env`나 애플리케이션 로그로 확인하지 않는다. 이름과 key 존재 여부만 다음으로 확인한다.

```bash
# Secret 이름 목록
kubectl -n openbake get secrets

# 특정 Secret의 key 이름만 확인 (값은 노출하지 않음)
kubectl -n openbake describe secret core-db-credentials

# 또는 jq로 key 이름만 추출 (값 자체는 Base64로도 출력하지 않음)
kubectl -n openbake get secret core-db-credentials -o json | jq '.data | keys'
```
