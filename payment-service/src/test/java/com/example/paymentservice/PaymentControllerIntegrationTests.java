package com.example.paymentservice;

import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.repository.PaymentRepository;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer coordinatorServer;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        coordinatorServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void shouldChargePaymentAndReportStepSuccess() throws Exception {
        coordinatorServer.expect(requestTo("http://localhost:8081/api/tx/tx-123/steps"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "stepId": 2
                        }
                        """, MediaType.APPLICATION_JSON));

        coordinatorServer.expect(requestTo("http://localhost:8081/api/tx/tx-123/steps/2/success"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/payments/charge")
                        .header("X-XID", "tx-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "ORD-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Payment charged"));

        Payment payment = paymentRepository.findByOrderRef("ORD-001").orElseThrow();
        assertThat(payment.getStatus()).isEqualTo("CHARGED");
        coordinatorServer.verify();
    }

    @Test
    void refundShouldBeIdempotent() throws Exception {
        Payment payment = new Payment();
        payment.setOrderRef("ORD-002");
        payment.setStatus("CHARGED");
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        mockMvc.perform(post("/payments/compensate/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "ORD-002"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Refunded"));

        mockMvc.perform(post("/payments/compensate/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "ORD-002"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Refunded"));

        assertThat(paymentRepository.findByOrderRef("ORD-002"))
                .get()
                .extracting(Payment::getStatus)
                .isEqualTo("REFUNDED");
    }
}
