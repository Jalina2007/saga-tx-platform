package com.example.coordinator;

import com.example.coordinator.entity.GlobalTransaction;
import com.example.coordinator.entity.GlobalTxStatus;
import com.example.coordinator.repository.GlobalTransactionRepository;
import com.example.coordinator.repository.TransactionStepRepository;
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

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GlobalTransactionRepository globalTransactionRepository;

    @Autowired
    private TransactionStepRepository transactionStepRepository;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer compensationServer;

    @BeforeEach
    void setUp() {
        transactionStepRepository.deleteAll();
        globalTransactionRepository.deleteAll();
        compensationServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void shouldBeginRegisterStepMarkSuccessAndQueryState() throws Exception {
        String xid = mockMvc.perform(post("/api/tx/begin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"xid\":\"([^\"]+)\".*", "$1");

        String orderStepId = mockMvc.perform(post("/api/tx/{xid}/steps", xid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "order-service",
                                  "actionName": "/orders",
                                  "compensationName": "/orders/compensate/cancel",
                                  "stepOrder": 1,
                                  "requestPayload": "orderRef=ORD-001",
                                  "compensationPayload": "orderRef=ORD-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"stepId\":(\\d+).*", "$1");

        String paymentStepId = mockMvc.perform(post("/api/tx/{xid}/steps", xid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "payment-service",
                                  "actionName": "/payments/charge",
                                  "compensationName": "/payments/compensate/refund",
                                  "stepOrder": 2,
                                  "requestPayload": "orderRef=ORD-001",
                                  "compensationPayload": "orderRef=ORD-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"stepId\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/tx/{xid}/steps/{stepId}/success", xid, orderStepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUCCESS",
                                  "message": "Order created"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(post("/api/tx/{xid}/steps/{stepId}/success", xid, paymentStepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUCCESS",
                                  "message": "Payment charged"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(get("/api/tx/{xid}", xid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xid").value(xid))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.steps[0].stepId").value(Integer.parseInt(orderStepId)))
                .andExpect(jsonPath("$.steps[0].serviceName").value("order-service"))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.steps[1].stepId").value(Integer.parseInt(paymentStepId)))
                .andExpect(jsonPath("$.steps[1].serviceName").value("payment-service"))
                .andExpect(jsonPath("$.steps[1].status").value("SUCCESS"));
    }

    @Test
    void shouldMarkTransactionFailedWhenStepFails() throws Exception {
        GlobalTransaction transaction = globalTransactionRepository.save(
                new GlobalTransaction("tx-failure", GlobalTxStatus.IN_PROGRESS, LocalDateTime.now())
        );

        String stepId = mockMvc.perform(post("/api/tx/{xid}/steps", transaction.getXid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "payment-service",
                                  "actionName": "/payments",
                                  "compensationName": "/payments/refund",
                                  "stepOrder": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"stepId\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/tx/{xid}/steps/{stepId}/failure", transaction.getXid(), stepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "FAILED",
                                  "message": "Payment declined"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(get("/api/tx/{xid}", transaction.getXid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPENSATED"))
                .andExpect(jsonPath("$.failureReason").value("Payment declined"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());
    }

    @Test
    void shouldCompensateSuccessfulStepsInReverseOrderWhenInventoryFails() throws Exception {
        compensationServer.expect(requestTo("http://localhost:8084/payments/compensate/refund"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("Refunded", MediaType.TEXT_PLAIN));

        compensationServer.expect(requestTo("http://localhost:8083/orders/compensate/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("Order cancelled", MediaType.TEXT_PLAIN));

        String xid = mockMvc.perform(post("/api/tx/begin"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"xid\":\"([^\"]+)\".*", "$1");

        String orderStepId = registerStep(xid, """
                {
                  "serviceName": "order-service",
                  "actionName": "/orders",
                  "compensationName": "/orders/compensate/cancel",
                  "stepOrder": 1,
                  "requestPayload": "{\\"orderRef\\":\\"ORD-FAIL-001\\"}",
                  "compensationPayload": "{\\"orderRef\\":\\"ORD-FAIL-001\\"}"
                }
                """);

        String paymentStepId = registerStep(xid, """
                {
                  "serviceName": "payment-service",
                  "actionName": "/payments/charge",
                  "compensationName": "/payments/compensate/refund",
                  "stepOrder": 2,
                  "requestPayload": "{\\"orderRef\\":\\"ORD-FAIL-001\\"}",
                  "compensationPayload": "{\\"orderRef\\":\\"ORD-FAIL-001\\"}"
                }
                """);

        String inventoryStepId = registerStep(xid, """
                {
                  "serviceName": "inventory-service",
                  "actionName": "/inventory/reserve",
                  "compensationName": "/inventory/compensate/release",
                  "stepOrder": 3,
                  "requestPayload": "{\\"orderRef\\":\\"ORD-FAIL-001\\"}",
                  "compensationPayload": "{\\"orderRef\\":\\"ORD-FAIL-001\\"}"
                }
                """);

        mockMvc.perform(post("/api/tx/{xid}/steps/{stepId}/success", xid, orderStepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUCCESS",
                                  "message": "Order created"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(post("/api/tx/{xid}/steps/{stepId}/success", xid, paymentStepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUCCESS",
                                  "message": "Payment charged"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(post("/api/tx/{xid}/steps/{stepId}/failure", xid, inventoryStepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "FAILED",
                                  "message": "Inventory unavailable"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        compensationServer.verify();

        mockMvc.perform(get("/api/tx/{xid}", xid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPENSATED"))
                .andExpect(jsonPath("$.failureReason").value("Inventory unavailable"))
                .andExpect(jsonPath("$.steps[0].serviceName").value("order-service"))
                .andExpect(jsonPath("$.steps[0].status").value("COMPENSATED"))
                .andExpect(jsonPath("$.steps[1].serviceName").value("payment-service"))
                .andExpect(jsonPath("$.steps[1].status").value("COMPENSATED"))
                .andExpect(jsonPath("$.steps[2].serviceName").value("inventory-service"))
                .andExpect(jsonPath("$.steps[2].status").value("FAILED"));
    }

    @Test
    void shouldReturnNotFoundForMissingTransaction() throws Exception {
        mockMvc.perform(get("/api/tx/{xid}", "missing-xid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found: missing-xid"));
    }

    private String registerStep(String xid, String body) throws Exception {
        return mockMvc.perform(post("/api/tx/{xid}/steps", xid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"stepId\":(\\d+).*", "$1");
    }
}
