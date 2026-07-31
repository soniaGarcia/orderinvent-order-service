package com.exam.order.controller;

import com.exam.order.dto.DeductStockRequest;
import com.exam.order.service.KafkaProducerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private KafkaProducerService kafkaProducerService;

    @Test
    @DisplayName("Integración POST /api/v1/orders - Retorna HTTP 201 Created y confirma orden")
    void createOrder_Integration_Returns201Created() throws Exception {
        // Mock de respuesta exitosa desde Inventory Service
        when(restTemplate.postForObject(anyString(), any(DeductStockRequest.class), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        String jsonPayload = """
            {
                "customerId": "CLI-INTEGRATION",
                "items": [
                    {
                        "productCode": "PROD-A100",
                        "quantity": 2
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.customerId").value("CLI-INTEGRATION"))
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));
    }
}