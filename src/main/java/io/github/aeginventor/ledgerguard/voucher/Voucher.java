package io.github.aeginventor.ledgerguard.voucher;

import io.github.aeginventor.ledgerguard.validation.Overall;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "voucher")
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    @Column(name = "description", length = 500, nullable = false)
    private String description;

    @Column(name = "counterparty_name", length = 200)
    private String counterpartyName;

    @Column(name = "counterparty_biz_no", length = 20)
    private String counterpartyBizNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", length = 20, nullable = false)
    private EvidenceType evidenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_type", length = 20, nullable = false)
    private VatType vatType;

    @Column(name = "supply_amount")
    private Long supplyAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private VoucherStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "drafter", length = 20, nullable = false)
    private DrafterType drafter;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_overall", length = 10, nullable = false)
    private Overall validationOverall;

    @Column(name = "preparer", length = 100)
    private String preparer;

    @Column(name = "approver", length = 100)
    private String approver;

    @Column(name = "approval_reason", length = 500)
    private String approvalReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<VoucherLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ValidationFinding> findings = new ArrayList<>();

    protected Voucher() {
    }

    public Voucher(LocalDate voucherDate, String description, String counterpartyName, String counterpartyBizNo,
            EvidenceType evidenceType, VatType vatType, Long supplyAmount, DrafterType drafter,
            String preparer, LocalDateTime createdAt) {
        this.voucherDate = voucherDate;
        this.preparer = preparer;
        this.description = description;
        this.counterpartyName = counterpartyName;
        this.counterpartyBizNo = counterpartyBizNo;
        this.evidenceType = evidenceType;
        this.vatType = vatType;
        this.supplyAmount = supplyAmount;
        this.drafter = drafter;
        this.createdAt = createdAt;
        this.status = VoucherStatus.DRAFT;
        this.validationOverall = Overall.PASS;
    }

    public void addLine(VoucherLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void replaceFindings(List<ValidationFinding> newFindings, Overall overall) {
        findings.clear();
        for (ValidationFinding finding : newFindings) {
            finding.attachTo(this);
            findings.add(finding);
        }
        this.validationOverall = overall;
    }

    public void changeStatus(VoucherStatus newStatus) {
        this.status = newStatus;
    }

    public void markPosted(String approver, String reason, LocalDateTime approvedAt) {
        this.status = VoucherStatus.POSTED;
        this.approver = approver;
        this.approvalReason = reason;
        this.approvedAt = approvedAt;
    }

    public long totalDebit() {
        return lines.stream()
                .filter(line -> line.getSide() == io.github.aeginventor.ledgerguard.account.Side.DEBIT)
                .mapToLong(VoucherLine::getAmount)
                .sum();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getVoucherDate() {
        return voucherDate;
    }

    public String getDescription() {
        return description;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public String getCounterpartyBizNo() {
        return counterpartyBizNo;
    }

    public EvidenceType getEvidenceType() {
        return evidenceType;
    }

    public VatType getVatType() {
        return vatType;
    }

    public Long getSupplyAmount() {
        return supplyAmount;
    }

    public VoucherStatus getStatus() {
        return status;
    }

    public DrafterType getDrafter() {
        return drafter;
    }

    public Overall getValidationOverall() {
        return validationOverall;
    }

    public String getPreparer() {
        return preparer;
    }

    public String getApprover() {
        return approver;
    }

    public String getApprovalReason() {
        return approvalReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public List<VoucherLine> getLines() {
        return lines;
    }

    public List<ValidationFinding> getFindings() {
        return findings;
    }
}
