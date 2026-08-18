# Phase 6 - Measurement-driven Optimization Experiment

## 목적

Phase 5는 `ReentrantLock` 내부 wait/hold/decrease 시간을 직접 계측했습니다.
Phase 6는 실측값이 없는 상태에서 성급하게 lock 구현을 교체하지 않고 다음을 자동화합니다.

1. Capacity 결과를 바탕으로 **무엇을 먼저 개선할지 Decision Gate 생성**
2. 실제 개선 후보를 적용했을 때 **Baseline vs Candidate 동일 조건 비교**
3. 응답시간뿐 아니라 비즈니스 오류/lock metric까지 같이 비교

## 1. 개선 방향 결정

Capacity Scan 후:

```bash
python3 experiments/optimization-decision.py
cat experiments/optimization-decision.md
```

결과 예:

- `LOCK SERIALIZATION CANDIDATE`
- `CRITICAL SECTION / decreaseQuantity CANDIDATE`
- `DB/CONNECTION PATH FIRST`
- `CPU PATH FIRST`
- `DEFER LOCK CHANGE`

이는 root cause 확정이 아니라 다음 실험의 우선순위입니다.

## 2. Baseline 반복 측정

예제 복사:

```bash
cp experiments/baseline-plan.example.csv experiments/baseline-plan.csv
```

각 `CHANGE_ME...`에 서로 다른 실제 Drop ID를 넣습니다.
상태 변경 API이므로 반복마다 독립 Drop이 필요합니다.

```bash
./experiments/run-variant.sh baseline experiments/baseline-plan.csv
```

## 3. Candidate 적용

`optimization-decision.md`와 `SOURCE_CHECKLIST.md`를 근거로 한 가지 개선만 적용합니다.
애플리케이션을 재빌드/재시작하고 candidate용 독립 Drop을 준비합니다.

```bash
cp experiments/candidate-plan.example.csv experiments/candidate-plan.csv
./experiments/run-variant.sh candidate experiments/candidate-plan.csv
```

## 4. 비교

```bash
python3 experiments/compare-variants.py
```

생성 파일:

```text
experiments/experiment-comparison.csv
experiments/experiment-comparison.md
```

비교 항목:

- k6 P95/P99
- Lock wait P95
- Lock hold P95
- decreaseQuantity P95
- CPU / Hikari pending
- lock timeout / invalid state / unexpected error

## 채택 기준

P95가 빨라졌다는 이유만으로 candidate를 채택하지 않습니다.

- 기능 오류가 증가하지 않아야 함
- 동일 VU, 독립 Drop, 가능한 동일 환경에서 비교
- baseline/candidate 각각 3회 이상 반복 권장
- P95/P99와 lock/server metric의 변화 방향이 설명 가능해야 함

`compare-variants.py`의 ±10% rule은 **편의상 표시 기준**일 뿐 통계적 유의성 검정이 아닙니다.
