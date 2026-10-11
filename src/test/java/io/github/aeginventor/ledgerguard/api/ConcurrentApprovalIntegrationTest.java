package io.github.aeginventor.ledgerguard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.aeginventor.ledgerguard.ledger.LedgerEntryRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 같은 전표를 두 요청이 동시에 승인하면 하나만 전기되어야 한다. voucher.version의 낙관적 락이 두 번째 갱신을 막고,
 * 핸들러가 409로 바꾼다. 원장에는 라인 3개만 들어가야 한다(두 번 전기되면 6개).
 */
class ConcurrentApprovalIntegrationTest extends ApiTestSupport {

    private static final LocalDate DATE = LocalDate.now().minusDays(2);

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @RepeatedTest(3)
    void onlyOneOfTwoSimultaneousApprovalsPostsTheVoucher() throws Exception {
        Map<String, Object> request = Map.of(
                "voucherDate", DATE.toString(),
                "description", "동시 승인 테스트",
                "preparer", "김작성",
                "counterpartyName", "동시승인상사 " + System.nanoTime(),
                "evidenceType", "CARD",
                "vatType", "TAXABLE",
                "supplyAmount", 100_000,
                "lines", List.of(line("830", "DEBIT", 100_000), line("135", "DEBIT", 10_000),
                        line("253", "CREDIT", 110_000)));
        JsonNode saved = postJson("/api/vouchers", request, 201);
        long id = saved.get("id").asLong();
        String body = objectMapper.writeValueAsString(Map.of("approver", "박승인"));

        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<String[]> approve = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            var response = mockMvc.perform(MockMvcRequestBuilders.post("/api/vouchers/" + id + "/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse();
            return new String[] {String.valueOf(response.getStatus()), response.getContentAsString()};
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<String[]>> futures = pool.invokeAll(List.of(approve, approve));
            for (Future<String[]> future : futures) {
                String[] result = future.get(30, TimeUnit.SECONDS);
                statuses.add(Integer.parseInt(result[0]));
                if (result[0].equals("409")) {
                    // 409의 사유가 낙관적 락 충돌인지, 먼저 커밋된 쪽을 읽은 뒤의 "이미 전기" 거부인지 기록한다
                    System.out.println("concurrent-approval 409: " + objectMapper.readTree(result[1]).get("message").asText());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(getJson("/api/vouchers/" + id, 200).get("status").asText()).isEqualTo("POSTED");
        assertThat(ledgerEntryRepository.findByVoucherIdOrderByLineNoAsc(id)).hasSize(3);
    }
}
