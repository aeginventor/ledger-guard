package io.github.aeginventor.ledgerguard.voucher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.ledger.LedgerEntryRepository;
import io.github.aeginventor.ledgerguard.voucher.dto.ApproveRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.VoucherLineRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.VoucherRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 낙관적 락을 결정적으로 재현한다. 트랜잭션 A가 전표(version 0)를 읽고 멈춘 사이에 트랜잭션 B가 승인해 커밋(version 1)하면,
 * A의 승인은 커밋 시점에 version 불일치로 실패해야 하고 원장에는 B의 라인 3개만 남아야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OptimisticLockTest {

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void staleApprovalFailsWithOptimisticLockAndPostsOnlyOnce() throws Exception {
        VoucherRequest request = new VoucherRequest(LocalDate.now().minusDays(3), "낙관적 락 테스트", "락테스트상사",
                null, EvidenceType.CARD, VatType.TAXABLE, 100_000L, null, "김작성",
                List.of(new VoucherLineRequest("830", Side.DEBIT, BigDecimal.valueOf(100_000), null),
                        new VoucherLineRequest("135", Side.DEBIT, BigDecimal.valueOf(10_000), null),
                        new VoucherLineRequest("253", Side.CREDIT, BigDecimal.valueOf(110_000), null)));
        Long id = voucherService.create(request).id();
        assertThat(voucherRepository.findById(id).orElseThrow().getVersion()).isZero();

        CountDownLatch aHasRead = new CountDownLatch(1);
        CountDownLatch bHasCommitted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> a = pool.submit(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        voucherRepository.findById(id).orElseThrow(); // version 0을 영속성 컨텍스트에 올린다
                        aHasRead.countDown();
                        try {
                            if (!bHasCommitted.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("B가 커밋하지 않았다");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        voucherService.approve(id, new ApproveRequest("박승인", null));
                    });
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });
            Future<Throwable> b = pool.submit(() -> {
                try {
                    if (!aHasRead.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("A가 읽지 않았다");
                    }
                    voucherService.approve(id, new ApproveRequest("최승인", null));
                    return null;
                } catch (Throwable t) {
                    return t;
                } finally {
                    bHasCommitted.countDown();
                }
            });

            assertThat(b.get(30, TimeUnit.SECONDS)).as("B의 승인은 성공해야 한다").isNull();
            Throwable aFailure = a.get(30, TimeUnit.SECONDS);
            assertThat(aFailure).as("A의 뒤늦은 승인은 낙관적 락으로 실패해야 한다")
                    .isInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            pool.shutdownNow();
        }

        Voucher posted = voucherRepository.findById(id).orElseThrow();
        assertThat(posted.getStatus()).isEqualTo(VoucherStatus.POSTED);
        assertThat(posted.getApprover()).isEqualTo("최승인");
        assertThat(posted.getVersion()).isEqualTo(1L);
        assertThat(ledgerEntryRepository.findByVoucherIdOrderByLineNoAsc(id)).hasSize(3);
    }
}
