package io.github.aeginventor.ledgerguard.voucher;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    @Query("""
            select v from Voucher v
            where (:status is null or v.status = :status)
              and (:from is null or v.voucherDate >= :from)
              and (:to is null or v.voucherDate < :to)
            order by v.voucherDate asc, v.id asc
            """)
    List<Voucher> search(@Param("status") VoucherStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 같은 일자, 같은 거래처의 전표 가운데 REJECTED가 아닌 것을 돌려준다. 총액 비교는 서비스 계층에서 한다.
     */
    @Query("""
            select v from Voucher v
            where v.voucherDate = :date
              and v.counterpartyName = :counterpartyName
              and v.status <> io.github.aeginventor.ledgerguard.voucher.VoucherStatus.REJECTED
              and (:excludeId is null or v.id <> :excludeId)
            """)
    List<Voucher> findDuplicateCandidates(@Param("date") LocalDate date,
            @Param("counterpartyName") String counterpartyName,
            @Param("excludeId") Long excludeId);
}
