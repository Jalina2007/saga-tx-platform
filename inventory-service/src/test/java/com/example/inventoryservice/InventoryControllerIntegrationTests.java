package com.example.inventoryservice;

import com.example.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer coordinatorServer;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        coordinatorServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void shouldReserveInventoryAndReportSuccess() throws Exception {
        coordinatorServer.expect(requestTo("http://localhost:8081/api/tx/tx-123/steps"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "stepId": 3
                        }
                        """, MediaType.APPLICATION_JSON));

        coordinatorServer.expect(requestTo("http://localhost:8081/api/tx/tx-123/steps/3/success"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/inventory/reserve")
                        .header("X-XID", "tx-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "ORD-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Reserved"));

        assertThat(inventoryRepository.findByOrderRef("ORD-001")).isPresent();
        coordinatorServer.verify();
    }

    @Test
    void shouldReportFailureWhenInventoryIsUnavailable() throws Exception {
        coordinatorServer.expect(requestTo("http://localhost:8081/api/tx/tx-fail/steps"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "stepId": 3
                        }
                        """, MediaType.APPLICATION_JSON));

        coordinatorServer.expect(requestTo("http://localhost:8081/api/tx/tx-fail/steps/3/failure"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> mockMvc.perform(post("/inventory/reserve")
                        .header("X-XID", "tx-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "ORD-FAIL-001"
                                }
                                """)))
                .hasRootCauseMessage("Inventory unavailable");

        assertThat(inventoryRepository.findByOrderRef("ORD-FAIL-001")).isEmpty();
        coordinatorServer.verify();
    }
}
