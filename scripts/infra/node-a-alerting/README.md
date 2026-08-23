# Node A 장애 알림·자동 복구 — 적용 산출물

설계 근거: [`docs/k3s-learning/15-node-a-failure-alerting-and-auto-recovery.md`](../../../docs/k3s-learning/15-node-a-failure-alerting-and-auto-recovery.md)

이슈 4의 작업 4다. **AWS 콘솔/CLI 조작과 Node A 서버 작업은 사람이 직접 한다.** 여기서는 적용할 내용만 스크립트와 문서로 정리했고, 실행하지 않았다.

## 무엇을 만드는가

`setup-cloudwatch-alarms.sh`는 15번 문서 3~4장의 알림·복구 경로를 만든다.

```text
SNS Topic: openbake-infra-alerts (신규 생성)
  ├── NodeA-SystemStatusCheckFailed (StatusCheckFailed_System, 2회 연속)
  │     └── alarm action: SNS 알림 + EC2 Recover
  └── NodeA-InstanceStatusCheckFailed (StatusCheckFailed_Instance, 2회 연속)
        └── alarm action: SNS 알림 + EC2 Reboot
```

두 알람은 원인이 다르므로(하드웨어 vs OS) alarm name과 description을 다르게 지어 알림 메시지에서 구분되게 했다. 같은 SNS Topic을 공유하지만 CloudWatch 알림 이메일의 제목에 alarm name이 포함되므로 어느 쪽인지 구분된다.

## 실행 전 확인

- `aws` CLI가 설치되어 있고 알람·SNS를 만들 수 있는 자격 증명으로 로그인되어 있어야 한다.
- t3.large가 대상 리전에서 [EC2 Auto Recovery를 지원하는지](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-recover.html) 사전에 확인한다.
- Node A가 이미 root EBS만 사용하고(`instance-store` 아님) `DeleteOnTermination=false`인지 확인한다(09번 문서 1장 전제).

## 실행

```bash
export AWS_REGION=ap-northeast-2
export NODE_A_INSTANCE_ID=i-xxxxxxxxxxxxxxxxx
export ALERT_EMAIL=you@example.com

./setup-cloudwatch-alarms.sh
```

실행 뒤 `ALERT_EMAIL`로 온 SNS 구독 확인 메일에서 Confirm을 눌러야 알림이 실제로 온다.

## systemd 확인 (Node A에서 직접)

k3s 설치 스크립트가 기본으로 구성하므로 새로 설정할 필요는 없고, 다음으로 확인만 한다.

```bash
systemctl is-enabled k3s   # enabled 여야 함
systemctl show k3s -p Restart   # Restart=always 여야 함
```

## blackbox_exporter는 이번 범위에 포함하지 않았다

15번 문서 3.2장은 Node B의 Prometheus에 `blackbox_exporter`를 추가해 Node A의 Traefik/Gateway health를 외부에서 probe하도록 설계했다. `k8s/monitoring/`(작업 1)에 함께 넣을 수도 있었지만, CloudWatch 알람·SNS 연결이 먼저 검증된 뒤에 추가하는 편이 더 안전하다고 판단해 이번 매니페스트에는 넣지 않았다. 필요해지면 `k8s/monitoring/prometheus/`에 `blackbox_exporter` Deployment/Service와 Prometheus `scrape_configs`의 `module: [http_2xx]` job을 추가한다.

## 자동으로 해결되지 않는 범위

15번 문서 5장 표를 그대로 따른다.

| 장애 유형 | 이 산출물의 대응 | 실제 발생 시 |
| --- | --- | --- |
| 호스트 하드웨어 장애 | Auto Recovery로 자동 복구 | 사람 개입 없음(수 분 다운타임) |
| OS 레벨 행 | Reboot 알람으로 자동 재부팅 시도 | 재부팅으로 해결되지 않으면 수동 개입 필요 |
| k3s 프로세스만 죽음 | systemd 자동 재시작(이미 기본값, 확인만 함) | 자동 복구, 반복 crash면 알림으로 확인 필요 |
| 인스턴스 terminate·EBS 손상 | 이 산출물의 대상 아님 | 09번 문서 16장의 수동 복구 절차 |

## 검증 (사람이 직접, 15번 문서 6장)

- SNS Topic에 테스트 메시지를 보내 이메일이 실제로 오는지 최초 1회 확인
- Node A를 계획된 점검 시간에 CloudWatch 콘솔에서 Reboot 알람을 수동 트리거해 k3s가 재부팅 후 자동으로 올라오는지 확인
- Auto Recovery는 직접 트리거하기 어려우므로 Recover 액션이 알람에 정확히 연결되어 있는지 `aws cloudwatch describe-alarms`로 정기 점검(09번 문서 18장 매월 점검 항목에 추가)
