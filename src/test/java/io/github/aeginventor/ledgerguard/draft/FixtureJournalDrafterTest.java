package io.github.aeginventor.ledgerguard.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixtureJournalDrafterTest {

    private final FixtureJournalDrafter drafter = new FixtureJournalDrafter(new ObjectMapper());

    private DraftRequest request(String description, VatType vatType, Long supply) {
        return new DraftRequest(description, LocalDate.of(2026, 10, 2), "가상상사", null, EvidenceType.CARD, vatType,
                supply);
    }

    @Test
    void loadsEveryFixtureFileSortedByPriority() {
        assertThat(drafter.fixtures()).hasSize(12);
        assertThat(drafter.fixtures().get(0).id()).isEqualTo("purchase_goods");
        assertThat(drafter.name()).isEqualTo("fixture");
    }

    @Test
    void fillsSupplyVatAndTotalForTaxablePurchase() {
        DraftResult result = drafter.draft(request("사무용품 카드 구입", VatType.TAXABLE, 123_456L), List.of());
        assertThat(result.problems()).isEmpty();
        assertThat(result.lines()).extracting(LineData::accountCode).containsExactly("830", "135", "253");
        assertThat(result.lines()).extracting(LineData::side).containsExactly(Side.DEBIT, Side.DEBIT, Side.CREDIT);
        assertThat(result.lines()).extracting(LineData::amount)
                .containsExactly(new BigDecimal("123456"), new BigDecimal("12345"), new BigDecimal("135801"));
        assertThat(result.note()).contains("실제 LLM 호출 아님");
    }

    @Test
    void dropsVatLineForExemptTransaction() {
        DraftResult result = drafter.draft(request("사무실 임차료 이체", VatType.EXEMPT, 500_000L), List.of());
        assertThat(result.lines()).extracting(LineData::accountCode).containsExactly("819", "103");
        assertThat(result.lines()).extracting(LineData::amount)
                .containsExactly(new BigDecimal("500000"), new BigDecimal("500000"));
    }

    @Test
    void choosesSettlementAccountByKeyword() {
        DraftResult cash = drafter.draft(request("거래처 식사 접대 현금", VatType.NA, 40_000L), List.of());
        assertThat(cash.lines()).extracting(LineData::accountCode).containsExactly("813", "101");
        DraftResult card = drafter.draft(request("거래처 식사 접대 카드", VatType.NA, 40_000L), List.of());
        assertThat(card.lines()).extracting(LineData::accountCode).containsExactly("813", "253");
    }

    @Test
    void salesDraftUsesOutputVatOnCredit() {
        DraftResult result = drafter.draft(request("상품 외상 판매", VatType.TAXABLE, 1_000_000L), List.of());
        assertThat(result.lines()).extracting(LineData::accountCode).containsExactly("108", "401", "255");
        assertThat(result.lines()).extracting(LineData::side).containsExactly(Side.DEBIT, Side.CREDIT, Side.CREDIT);
    }

    @Test
    void leavesAmountsEmptyWhenSupplyAmountIsMissing() {
        DraftResult result = drafter.draft(request("사무용품 구입", VatType.TAXABLE, null), List.of());
        assertThat(result.lines()).hasSize(3);
        assertThat(result.lines()).extracting(LineData::amount).containsOnlyNulls();
    }

    @Test
    void reportsProblemWhenNoFixtureMatches() {
        DraftResult result = drafter.draft(request("알 수 없는 거래 xyz", VatType.TAXABLE, 10_000L), List.of());
        assertThat(result.lines()).isEmpty();
        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().get(0)).contains("고정 초안이 없다");
    }
}
