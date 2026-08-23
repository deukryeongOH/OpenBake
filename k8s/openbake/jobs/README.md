# PostgreSQL 백업 CronJob

4개 DB(core/member/payment/ai)를 매일 새벽 dump해 S3에 올린다.
설계 근거: `docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md`

```
postgres-backup-core      02:10 KST
postgres-backup-member    02:20 KST
postgres-backup-payment   02:30 KST
postgres-backup-ai        02:40 KST
```

시각을 10분씩 어긋나게 둔 것은 Node A 메모리 때문이다. 동시에 돌리지 않는다.

## dump는 암호화해서 올린다

백업 버킷이 **앱 이미지 서빙과 같은 버킷**(`team06-s3-bakerysite6`)이다. 전용 버킷을 만들 수 없어
`postgres/<db>/<date>/` prefix로 분리해 쓰지만, 이 버킷의 공개 정책을 읽을 권한이 없다
(IAM 키에 `GetBucketPolicyStatus` 없음). **버킷 정책에 기대지 않기로 하고 dump 자체를 암호화한다.**

initContainer(`postgres:17`)가 dump 직후 암호화하고 평문을 지운다. 업로드 컨테이너는 `.enc`만 본다.

```
pg_dump -Fc  →  pg_restore --list 로 무결성 확인  →  openssl enc -aes-256-cbc -pbkdf2 -iter 200000
             →  평문 rm  →  aws s3 cp *.enc
```

S3에 올라가는 객체는 `postgres/<db>/<date>/<dbname>.dump.enc`다.

### passphrase

`backup-encryption-credentials` Secret의 `BACKUP_PASSPHRASE` 하나를 4개 Job이 공유한다.

```bash
cd k8s/secrets
./apply-secrets.sh scaffold     # /opt/openbake/secrets/backup-encryption.env 생성
openssl rand -base64 48         # 이 값을 넣는다
sudoedit /opt/openbake/secrets/backup-encryption.env
./apply-secrets.sh apply
./apply-secrets.sh verify
```

> **이 값을 잃어버리면 백업은 전부 복원 불가능한 쓰레기가 된다.**
> 클러스터 밖(팀 password manager 등)에 반드시 별도 보관한다.
> 클러스터 Secret만 갖고 있는 상태는 "클러스터가 죽으면 백업도 못 쓴다"는 뜻이라 백업이 아니다.

### 복호화

```bash
aws s3 cp "s3://team06-s3-bakerysite6/postgres/core/2026/08/23/openbake.dump.enc" . \
  --region ap-northeast-2

read -rs -p "passphrase: " BACKUP_PASSPHRASE; export BACKUP_PASSPHRASE; echo
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -pass env:BACKUP_PASSPHRASE \
  -in openbake.dump.enc -out openbake.dump
unset BACKUP_PASSPHRASE

pg_restore --list openbake.dump | head    # 복호화가 제대로 됐는지 먼저 확인
```

`pg_restore --list`가 깨지면 passphrase가 틀린 것이다. openssl은 틀린 passphrase에도
"bad decrypt" 정도만 내고 조용히 쓰레기를 뱉을 수 있으니 **이 확인을 건너뛰지 않는다.**

### `openssl`이 image에 없을 경우 — SSE-C 대안

initContainer는 시작할 때 `command -v openssl`로 먼저 확인하고, 없으면 즉시 실패한다.
`postgres:17`에 `openssl` 바이너리가 없다면 암호화 방식을 SSE-C로 바꾼다.
`aws` CLI만으로 되므로 추가 바이너리가 필요 없다.

업로드 컨테이너를 이렇게 바꾸고, initContainer의 openssl 관련 3줄을 지운다.

```bash
aws s3 cp /backup/openbake.dump "s3://$BACKUP_S3_BUCKET/$KEY" --region "$AWS_REGION" \
  --sse-c AES256 --sse-c-key "$BACKUP_PASSPHRASE"
aws s3api head-object --bucket "$BACKUP_S3_BUCKET" --key "$KEY" --region "$AWS_REGION" \
  --sse-customer-algorithm AES256 --sse-customer-key "$BACKUP_PASSPHRASE" \
  --query ContentLength --output text
```

SSE-C는 key를 모르면 `GET`이 아예 실패하므로 버킷이 공개여도 읽히지 않는다.
단 key가 정확히 **32바이트**여야 한다 (`openssl rand -base64 24`).
`BACKUP_PASSPHRASE`를 그대로 쓰려면 길이를 맞춰서 생성한다.

## 수동 실행

```bash
kubectl -n openbake create job --from=cronjob/postgres-backup-core manual-core-$(date +%s)
kubectl -n openbake wait --for=condition=complete job/manual-core-... --timeout=600s
kubectl -n openbake logs job/manual-core-... --all-containers
```

initContainer(`dump`)와 container(`upload`) 중 어디서 실패했는지부터 본다.
`upload`의 `AccessDenied`는 IAM 키에 `s3:PutObject`가 없는 것이다.

## NetworkPolicy

`openbake`의 default-deny는 **Ingress만** 건다. 따라서 S3로 나가는 egress는 막히지 않는다.
DB로 들어가는 쪽은 `allow-<app>-to-<db>-postgres`가 `app.kubernetes.io/name: postgres-backup`
라벨을 이미 허용한다. 임시 복구 Pod를 만들 때도 **이 라벨을 붙여야** DB에 붙을 수 있다.
