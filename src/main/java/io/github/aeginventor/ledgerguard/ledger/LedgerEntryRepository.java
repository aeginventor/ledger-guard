package io.github.aeginventor.ledgerguard.ledger;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByVoucherIdOrderByLineNoAsc(Long voucherId);

    @Query("""
            select e.accountCode, e.side, sum(e.amount)
            from LedgerEntry e
            where e.entryDate >= :from and e.entryDate < :to
            group by e.accountCode, e.side
            order by e.accountCode asc
            """)
    List<Object[]> sumByAccountAndSide(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
