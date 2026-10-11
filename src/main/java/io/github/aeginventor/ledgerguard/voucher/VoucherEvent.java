package io.github.aeginventor.ledgerguard.voucher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 전표 상태 이력 한 줄. 작성(CREATED), 승인과 전기(APPROVED), 역분개로 상쇄됨(REVERSED). */
@Entity
@Table(name = "voucher_event")
public class VoucherEvent {

    public enum Type { CREATED, APPROVED, REVERSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_id", nullable = false)
    private Long voucherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 20, nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private VoucherStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20, nullable = false)
    private VoucherStatus toStatus;

    @Column(name = "actor", length = 100)
    private String actor;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected VoucherEvent() {
    }

    public VoucherEvent(Long voucherId, Type type, VoucherStatus fromStatus, VoucherStatus toStatus, String actor,
            String reason, LocalDateTime occurredAt) {
        this.voucherId = voucherId;
        this.type = type;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getVoucherId() {
        return voucherId;
    }

    public Type getType() {
        return type;
    }

    public VoucherStatus getFromStatus() {
        return fromStatus;
    }

    public VoucherStatus getToStatus() {
        return toStatus;
    }

    public String getActor() {
        return actor;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
