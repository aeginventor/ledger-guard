package io.github.aeginventor.ledgerguard.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @Column(name = "code", length = 10, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_side", length = 10, nullable = false)
    private Side normalSide;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "vat_related", nullable = false)
    private boolean vatRelated;

    protected Account() {
    }

    public Account(String code, String name, AccountType type, Side normalSide, boolean active, boolean vatRelated) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.normalSide = normalSide;
        this.active = active;
        this.vatRelated = vatRelated;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public Side getNormalSide() {
        return normalSide;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isVatRelated() {
        return vatRelated;
    }
}
