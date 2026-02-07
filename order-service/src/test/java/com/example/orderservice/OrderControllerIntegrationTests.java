package com.example.orderservice;

import com.example.orderservice.entity.Order;
import com.example.orderservice.repository.OrderRepository;
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
class OrderControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer coordinatorServer;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        coordinatorServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void shouldCreateOrderAndReportStepSuccess() throws Exception {
        coordinatorServer.expect(requestTo("http://localhost:8081/api/tx/tx-123/steps"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "stepId": 1
                        }
                        """, MediaType.APPLICATION_JSON));

        coordinatorServer.expect(requestTo("http://localhost:8081/api/tx/tx-123/steps/1/success"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/orders")
                        .header("X-XID", "tx-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "ORD-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Order created"));

        Order order = orderRepository.findByOrderRef("ORD-001").orElseThrow();
        assertThat(order.getStatus()).isEqualTo("CREATED");
        coordinatorServer.verify();
    }

    @Test
    void cancelOrderShouldBeIdempotent() throws Exception {
        Order order = new Order();
        order.setOrderRef("ORD-002");
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        orderRepository.save(order);

        mockMvc.perform(post("/orders/compensate/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "ORD-002"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Order cancelled"));

        mockMvc.perform(post("/orders/compensate/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "ORD-002"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Order cancelled"));

        assertThat(orderRepository.findByOrderRef("ORD-002"))
                .get()
                .extracting(Order::getStatus)
                .isEqualTo("CANCELLED");
    }
}
