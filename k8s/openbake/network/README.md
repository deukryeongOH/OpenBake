# k8s/openbake/network/

`06-namespace-label-networkpolicy-design.md`의 ingress 허용표를 그대로 옮긴 NetworkPolicy. 단계 1(ingress 기본 차단)만 다루며, egress는 제한하지 않는다(06번 문서 9장 판단 유지).

## 적용 순서

`allow`를 `default-deny`보다 먼저 적용한다. 순서를 바꾸면 allow policy가 배포되기 전까지 정상 통신이 끊긴다.

```bash
kubectl apply -k k8s/openbake/network/allow
kubectl apply -k k8s/openbake/network/default-deny
```

## Kafka·ai-service 갱신 내역

06번 문서 10장은 "Kafka와 AI는 구현 전이므로 실제 producer/consumer가 확정되면 정책을 갱신한다"고 명시했다. `docker-compose.yaml`과 실제 코드(`@KafkaListener`, `KafkaTemplate`)를 확인한 결과는 다음과 같다.

| 워크로드 | Kafka 역할 | 근거 |
| --- | --- | --- |
| backend(root 모듈) | producer | `src/main/java/.../common/outbox/KafkaOutboxSender.java`, `ProductViewedKafkaPublisher.java` |
| member-service | producer | `member-service/.../outbox/KafkaOutboxSender.java` |
| ai-service | consumer | `ProductChangedEventConsumer`, `MemberWithdrawnEventConsumer`, `MemberInteractionEventConsumer` |
| payment-service, api-gateway | 없음 | Kafka 관련 코드 없음 |

`openbake` Namespace 전체를 Kafka에 허용하지 않고, 위 3개 워크로드만 `allow-to-kafka.yaml`에 명시했다.

포트는 06번 문서 표기(9092)가 아니라 `docker-compose.yaml`의 실제 내부 리스너 `KAFKA_LISTENERS: PLAINTEXT://:29092`를 따라 **29092**를 사용했다. 9092는 host 노출용 `PLAINTEXT_HOST` 리스너다.

ai-service는 Elasticsearch도 직접 호출한다(`ELASTICSEARCH_URIS`). 06번 문서 작성 시점에는 없던 호출이라 `allow-backend-to-elasticsearch.yaml`에 ai-service를 소스로 추가했다.

## 이슈 3으로 미룬 것

- Kafka, ai-postgres, PostgreSQL 3개, Redis, Elasticsearch StatefulSet 자체는 아직 없다. 위 NetworkPolicy는 label selector만 정의해 둔 것이라, 실제 Pod가 뜨기 전까지는 `kubectl describe networkpolicy`로 selector 확인만 가능하고 실제 연결 테스트는 이슈 3 이후에 한다.
- `postgres-backup` CronJob(component: backup)도 이슈 3 대상. `allow-*-postgres` 정책에는 이미 소스로 넣어 두었으므로 CronJob이 생기면 label만 맞추면 된다.
- default-deny 적용 뒤 회귀 테스트(허용/차단 시나리오 전체)는 워크로드가 배포된 뒤 실행한다.

## 지금 확인한 것

- 워크로드가 없는 상태에서 `kubectl kustomize`, `kubeconform`, `kubectl apply --dry-run=server`로 매니페스트 자체의 스키마·selector 유효성만 검증했다(아래 "검증 결과" 참고).
- 실제 Pod 간 연결(허용/차단) 테스트는 데이터·애플리케이션 계층이 배포된 이슈 3 이후에 임시 Pod(`kubectl run --rm -it`)로 수행한다.
