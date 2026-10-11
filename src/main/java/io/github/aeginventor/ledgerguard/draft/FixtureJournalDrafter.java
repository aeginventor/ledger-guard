package io.github.aeginventor.ledgerguard.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.rules.R04VatAmountRule;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 실제 LLM을 호출하지 않는 기본 작성기. drafts/*.json의 키워드가 거래 설명에 들어 있으면 그 고정 초안을 금액만 채워 돌려준다.
 * 어느 초안과도 맞지 않으면 problems에 사유를 담아 돌려주고 R11이 FAIL로 판정한다.
 */
@Component
public class FixtureJournalDrafter implements JournalDrafter {

    public static final String NAME = "fixture";

    private final List<DraftFixture> fixtures;

    public FixtureJournalDrafter(ObjectMapper objectMapper) {
        this.fixtures = load(objectMapper);
    }

    static List<DraftFixture> load(ObjectMapper objectMapper) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:drafts/*.json");
            List<DraftFixture> loaded = new ArrayList<>();
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    loaded.add(objectMapper.readValue(in, DraftFixture.class));
                }
            }
            loaded.sort(Comparator.comparingInt(DraftFixture::priority).thenComparing(DraftFixture::id));
            return List.copyOf(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("drafts/*.json을 읽지 못했다", e);
        }
    }

    public List<DraftFixture> fixtures() {
        return fixtures;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public DraftResult draft(DraftRequest request, List<Account> accounts) {
        String description = request.description() == null ? "" : request.description();
        Optional<DraftFixture> matched = fixtures.stream()
                .filter(f -> f.keywords().stream().anyMatch(description::contains))
                .findFirst();
        if (matched.isEmpty()) {
            return DraftResult.failed("거래 설명과 맞는 고정 초안이 없다: " + description);
        }
        DraftFixture fixture = matched.get();
        Long supply = request.supplyAmount();
        boolean taxable = request.vatType() == VatType.TAXABLE;
        Long vat = supply == null ? null : taxable ? R04VatAmountRule.expectedVat(supply) : 0L;
        Long total = supply == null ? null : supply + vat;
        String settlement = resolveSettlement(fixture.settlement(), description);

        List<LineData> lines = new ArrayList<>();
        for (DraftFixture.Line line : fixture.lines()) {
            if ("VAT".equals(line.amount()) && !taxable) {
                continue;
            }
            String code = "@settlement".equals(line.accountCode()) ? settlement : line.accountCode();
            Long amount = switch (line.amount()) {
                case "SUPPLY" -> supply;
                case "VAT" -> vat;
                case "TOTAL" -> total;
                default -> null;
            };
            lines.add(new LineData(lines.size() + 1, code, Side.valueOf(line.side()),
                    amount == null ? null : BigDecimal.valueOf(amount), line.memo()));
        }
        return new DraftResult(lines, List.of(), "fixture " + fixture.id() + " 적용. 실제 LLM 호출 아님");
    }

    private static String resolveSettlement(DraftFixture.Settlement settlement, String description) {
        if (settlement == null) {
            return null;
        }
        Map<String, String> keywords = settlement.keywords() == null ? Map.of() : settlement.keywords();
        return keywords.entrySet().stream()
                .filter(entry -> description.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(settlement.defaultAccount());
    }

    static List<String> describe(DraftFixture fixture) {
        return Arrays.asList(fixture.id(), String.join(",", fixture.keywords()));
    }
}
