package io.github.aeginventor.ledgerguard.voucher;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherEventRepository extends JpaRepository<VoucherEvent, Long> {

    List<VoucherEvent> findByVoucherIdOrderByIdAsc(Long voucherId);
}
