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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

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

    @BeforeEach
    void setUp() {
        transactionStepRepository.deleteAll();
        globalTransactionRepository.deleteAll();
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

        String stepId = mockMvc.perform(post("/api/tx/{xid}/steps", xid)
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

        mockMvc.perform(post("/api/tx/{xid}/steps/{stepId}/success", xid, stepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUCCESS",
                                  "message": "Order created"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(get("/api/tx/{xid}", xid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xid").value(xid))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.steps[0].stepId").value(Integer.parseInt(stepId)))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCESS"));
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
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Payment declined"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());
    }

    @Test
    void shouldReturnNotFoundForMissingTransaction() throws Exception {
        mockMvc.perform(get("/api/tx/{xid}", "missing-xid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found: missing-xid"));
    }
}
