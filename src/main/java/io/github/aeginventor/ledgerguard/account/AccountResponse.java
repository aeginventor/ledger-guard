package io.github.aeginventor.ledgerguard.account;

public record AccountResponse(
        String code,
        String name,
        AccountType type,
        Side normalSide,
        boolean active,
        boolean vatRelated) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getCode(),
                account.getName(),
                account.getType(),
                account.getNormalSide(),
                account.isActive(),
                account.isVatRelated());
    }
}
