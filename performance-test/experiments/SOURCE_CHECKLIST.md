# Candidate 성능 개선 전에 필요한 실제 소스

Phase 6에서는 **증거 없이 lock 구현을 바꾸지 않습니다.**
현재 `DropLockFacade`에서 lock 안에 들어가는 핵심 호출은 `dropLockService.decreaseQuantity(...)`이므로,
실제 최적화 코드를 만들려면 최소 아래 소스의 현재 버전이 필요합니다.

- `DropLockService.java`
- `DropInventoryRepository.java`
- `DropInventoryJpaRepository.java`
- `DropInventory.java`
- `decreaseQuantity()`에서 호출하는 Cart/CartItem service 또는 repository
- `Drop` 상태를 `COMPLETED`로 변경하는 코드 경로
- 각 메서드의 `@Transactional` 경계

확인할 질문:

1. `decreaseQuantity()`가 DB SELECT → entity 변경 → UPDATE 구조인지, 조건부 UPDATE 한 번인지
2. 장바구니 생성/수정이 같은 transaction과 lock 안에 들어가는지
3. 재고 0일 때 Drop 상태 변경이 같은 transaction에 들어가는지
4. 동일 회원의 중복 선점/멱등성 제약이 DB에 있는지
5. 애플리케이션 인스턴스를 2개 이상 띄울 계획이 있는지

> `ReentrantLock`은 한 JVM 안에서만 동기화합니다. 다중 인스턴스 전환 여부는 lock 전략 선택의 필수 입력입니다.
