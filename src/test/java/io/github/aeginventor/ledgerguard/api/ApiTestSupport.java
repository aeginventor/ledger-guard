package io.github.aeginventor.ledgerguard.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability // 테스트에서는 지표 내보내기가 기본으로 꺼지므로 /actuator/prometheus 확인을 위해 켠다
@ActiveProfiles("test")
abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected JsonNode postJson(String path, Object body, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
        int status = result.getResponse().getStatus();
        String content = result.getResponse().getContentAsString();
        if (status != expectedStatus) {
            throw new AssertionError("POST " + path + " expected " + expectedStatus + " but was " + status + ": " + content);
        }
        return content.isEmpty() ? objectMapper.nullNode() : objectMapper.readTree(content);
    }

    protected JsonNode getJson(String path, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(path)).andReturn();
        int status = result.getResponse().getStatus();
        String content = result.getResponse().getContentAsString();
        if (status != expectedStatus) {
            throw new AssertionError("GET " + path + " expected " + expectedStatus + " but was " + status + ": " + content);
        }
        return objectMapper.readTree(content);
    }

    protected static Map<String, Object> line(String accountCode, String side, long amount) {
        return Map.of("accountCode", accountCode, "side", side, "amount", amount);
    }
}
