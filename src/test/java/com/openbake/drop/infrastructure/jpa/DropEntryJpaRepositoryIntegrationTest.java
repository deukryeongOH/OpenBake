package com.openbake.drop.infrastructure.jpa;

import com.openbake.drop.domain.EntryStatus;
import com.openbake.drop.domain.entity.DropEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import com.openbake.product.infrastructure.elasticsearch.ProductSearchRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DropEntryJpaRepository.fail()의 CASE WHEN 분기(docs/10 3.1절)와 sumReservedQuantity()의
 * COMPLETED 포함 여부는 실제 SQL 동작이라 Mockito로는 검증할 수 없다. 실제 DB(H2)로 확인한다.
 *
 * 클래스 레벨 @Transactional — @Modifying 쿼리는 활성 트랜잭션이 필요하고,
 * 테스트마다 자동 롤백돼 별도 정리 코드 없이 격리된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DropEntryJpaRepositoryIntegrationTest {

    // 이 테스트는 검색과 무관하지만 @SpringBootTest가 전체 컨텍스트를 올려 ES 빈까지 생성한다.
    // SimpleElasticsearchRepository는 생성 시점에 실제 접속을 시도하므로 로컬에 ES 없이도 통과하도록 대체한다.
    @MockitoBean
    private ProductSearchRepository productSearchRepository;

    @MockitoBean
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private DropEntryJpaRepository dropEntryJpaRepository;

    private final Long dropId = 1L;
    private final Long memberId = 10L;

    private DropEntry save(EntryStatus status, int selectQuantity) {
        DropEntry entry = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(status).build();
        ReflectionTestUtils.setField(entry, "selectQuantity", selectQuantity);
        return dropEntryJpaRepository.saveAndFlush(entry);
    }

    @Test
    @DisplayName("RESERVED를 롤백하면 FAILED로 전환된다")
    void fail_FromReserved_BecomesFailed() {
        save(EntryStatus.RESERVED, 3);

        int affected = dropEntryJpaRepository.fail(dropId, memberId);

        assertThat(affected).isEqualTo(1);
        DropEntry reloaded = dropEntryJpaRepository.findByDropIdAndMemberId(dropId, memberId).orElseThrow();
        assertThat(reloaded.getEntryStatus()).isEqualTo(EntryStatus.FAILED);
    }

    @Test
    @DisplayName("결제 완료(COMPLETED)를 롤백하면 CANCELLED로 전환된다 - FAILED와 구분된다")
    void fail_FromCompleted_BecomesCancelled() {
        save(EntryStatus.COMPLETED, 3);

        int affected = dropEntryJpaRepository.fail(dropId, memberId);

        assertThat(affected).isEqualTo(1);
        DropEntry reloaded = dropEntryJpaRepository.findByDropIdAndMemberId(dropId, memberId).orElseThrow();
        assertThat(reloaded.getEntryStatus()).isEqualTo(EntryStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 FAILED인 항목은 다시 롤백되지 않는다 - 이중 롤백 방지")
    void fail_AlreadyFailed_DoesNothing() {
        save(EntryStatus.FAILED, 3);

        int affected = dropEntryJpaRepository.fail(dropId, memberId);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("이미 CANCELLED인 항목은 다시 롤백되지 않는다 - 이중 롤백 방지")
    void fail_AlreadyCancelled_DoesNothing() {
        save(EntryStatus.CANCELLED, 3);

        int affected = dropEntryJpaRepository.fail(dropId, memberId);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("RESERVED를 확정하면 COMPLETED로 전환된다")
    void complete_FromReserved_BecomesCompleted() {
        save(EntryStatus.RESERVED, 3);

        int affected = dropEntryJpaRepository.complete(dropId, memberId);

        assertThat(affected).isEqualTo(1);
        DropEntry reloaded = dropEntryJpaRepository.findByDropIdAndMemberId(dropId, memberId).orElseThrow();
        assertThat(reloaded.getEntryStatus()).isEqualTo(EntryStatus.COMPLETED);
    }

    @Test
    @DisplayName("이미 COMPLETED인 항목은 다시 확정되지 않는다 - 결제 결과 재전송에도 안전하다")
    void complete_AlreadyCompleted_DoesNothing() {
        save(EntryStatus.COMPLETED, 3);

        int affected = dropEntryJpaRepository.complete(dropId, memberId);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("ENTERED 상태는 확정 대상이 아니다 - 재고를 선점한 적 없는 항목을 확정하면 안 된다")
    void complete_FromEntered_DoesNothing() {
        save(EntryStatus.ENTERED, 0);

        int affected = dropEntryJpaRepository.complete(dropId, memberId);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("선점 합계는 RESERVED와 COMPLETED를 모두 더한다 - 결제 완료분도 재고가 나간 것이다")
    void sumReservedQuantity_IncludesReservedAndCompleted() {
        save(EntryStatus.RESERVED, 3);

        Long otherMember = memberId + 1;
        DropEntry completedEntry = DropEntry.builder()
                .dropId(dropId).memberId(otherMember).entryStatus(EntryStatus.COMPLETED).build();
        ReflectionTestUtils.setField(completedEntry, "selectQuantity", 2);
        dropEntryJpaRepository.saveAndFlush(completedEntry);

        int sum = dropEntryJpaRepository.sumReservedQuantity(dropId);

        assertThat(sum).isEqualTo(5);
    }

    @Test
    @DisplayName("선점 합계는 FAILED/CANCELLED는 제외한다 - 재고가 이미 반환된 항목이다")
    void sumReservedQuantity_ExcludesFailedAndCancelled() {
        DropEntry failedEntry = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.FAILED).build();
        ReflectionTestUtils.setField(failedEntry, "selectQuantity", 3);
        dropEntryJpaRepository.saveAndFlush(failedEntry);

        Long otherMember = memberId + 1;
        DropEntry cancelledEntry = DropEntry.builder()
                .dropId(dropId).memberId(otherMember).entryStatus(EntryStatus.CANCELLED).build();
        ReflectionTestUtils.setField(cancelledEntry, "selectQuantity", 2);
        dropEntryJpaRepository.saveAndFlush(cancelledEntry);

        int sum = dropEntryJpaRepository.sumReservedQuantity(dropId);

        assertThat(sum).isEqualTo(0);
    }

    @Test
    @DisplayName("선점(reserve)하면 reservedAt이 DB 시각으로 채워진다 - 만료 스위퍼의 기준값이다")
    void reserve_SetsReservedAt() {
        DropEntry entry = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.ENTERED).build();
        dropEntryJpaRepository.saveAndFlush(entry);

        LocalDateTime before = LocalDateTime.now();
        int affected = dropEntryJpaRepository.reserve(dropId, memberId, 3);
        LocalDateTime after = LocalDateTime.now();

        assertThat(affected).isEqualTo(1);
        DropEntry reloaded = dropEntryJpaRepository.findByDropIdAndMemberId(dropId, memberId).orElseThrow();
        assertThat(reloaded.getEntryStatus()).isEqualTo(EntryStatus.RESERVED);
        // DB 시각(H2)과 테스트 JVM 시각이 완전히 같은 클럭은 아니라 초 단위 여유를 둔다.
        assertThat(reloaded.getReservedAt())
                .isAfterOrEqualTo(before.minusSeconds(5))
                .isBeforeOrEqualTo(after.plusSeconds(5));
    }

    @Test
    @DisplayName("방치된 선점 조회는 cutoff 이전에 선점된 RESERVED 항목만 반환한다")
    void findExpiredReservations_ReturnsOnlyStaleReservedEntries() {
        LocalDateTime now = LocalDateTime.now();

        DropEntry stale = save(EntryStatus.RESERVED, 3);
        ReflectionTestUtils.setField(stale, "reservedAt", now.minusMinutes(20));
        dropEntryJpaRepository.saveAndFlush(stale);

        Long freshMember = memberId + 1;
        DropEntry fresh = DropEntry.builder()
                .dropId(dropId).memberId(freshMember).entryStatus(EntryStatus.RESERVED).build();
        ReflectionTestUtils.setField(fresh, "selectQuantity", 1);
        ReflectionTestUtils.setField(fresh, "reservedAt", now.minusMinutes(1));
        dropEntryJpaRepository.saveAndFlush(fresh);

        Long completedMember = memberId + 2;
        DropEntry completed = DropEntry.builder()
                .dropId(dropId).memberId(completedMember).entryStatus(EntryStatus.COMPLETED).build();
        ReflectionTestUtils.setField(completed, "selectQuantity", 1);
        ReflectionTestUtils.setField(completed, "reservedAt", now.minusMinutes(20));
        dropEntryJpaRepository.saveAndFlush(completed);

        List<DropEntry> expired = dropEntryJpaRepository.findExpiredReservations(now.minusMinutes(15));

        assertThat(expired).extracting(DropEntry::getMemberId).containsExactly(memberId);
    }
}