package com.example.demoorch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer backendServer;

    @BeforeEach
    void setUp() {
        backendServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void shouldBeginTransactionAndCallOrderPaymentAndInventoryWithSameXid() throws Exception {
        backendServer.expect(requestTo("http://localhost:8081/api/tx/begin"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "xid": "tx-123",
                          "status": "STARTED"
                        }
                        """, MediaType.APPLICATION_JSON));

        backendServer.expect(requestTo("http://localhost:8083/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-XID", "tx-123"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockRestRequestMatchers.content().json("""
                        {
                          "orderRef": "ORD-100"
                        }
                        """))
                .andRespond(withSuccess("Order created", MediaType.TEXT_PLAIN));

        backendServer.expect(requestTo("http://localhost:8084/payments/charge"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-XID", "tx-123"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockRestRequestMatchers.content().json("""
                        {
                          "orderRef": "ORD-100"
                        }
                        """))
                .andRespond(withSuccess("Payment charged", MediaType.TEXT_PLAIN));

        backendServer.expect(requestTo("http://localhost:8085/inventory/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-XID", "tx-123"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockRestRequestMatchers.content().json("""
                        {
                          "orderRef": "ORD-100"
                        }
                        """))
                .andRespond(withSuccess("Reserved", MediaType.TEXT_PLAIN));

        mockMvc.perform(post("/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "ORD-100"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Checkout complete"));

        backendServer.verify();
    }

    @Test
    void shouldPropagateFailureWhenInventoryReserveFails() throws Exception {
        backendServer.expect(requestTo("http://localhost:8081/api/tx/begin"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "xid": "tx-fail",
                          "status": "STARTED"
                        }
                        """, MediaType.APPLICATION_JSON));

        backendServer.expect(requestTo("http://localhost:8083/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-XID", "tx-fail"))
                .andRespond(withSuccess("Order created", MediaType.TEXT_PLAIN));

        backendServer.expect(requestTo("http://localhost:8084/payments/charge"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-XID", "tx-fail"))
                .andRespond(withSuccess("Payment charged", MediaType.TEXT_PLAIN));

        backendServer.expect(requestTo("http://localhost:8085/inventory/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-XID", "tx-fail"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> mockMvc.perform(post("/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "ORD-FAIL-001"
                                }
                                """)))
                .hasRootCauseInstanceOf(org.springframework.web.client.HttpServerErrorException.InternalServerError.class);

        backendServer.verify();
    }
}
