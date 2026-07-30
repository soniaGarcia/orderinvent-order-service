package com.exam.order.service;

import com.exam.order.dto.DeductStockRequest;
import com.exam.order.dto.OrderItemRequest;
import com.exam.order.dto.OrderRequest;
import com.exam.order.dto.OrderResponse;
import com.exam.order.model.Order;
import com.exam.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "inventoryServiceUrl", "http://localhost:8081/api/v1/inventory");
    }

    @Test
    @DisplayName("Debe confirmar la orden cuando hay stock suficiente en Inventory Service")
    void createOrder_Success_WhenStockIsAvailable() {
        // Arrange
        OrderRequest request = buildSampleOrderRequest();
        
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            if (savedOrder.getId() == null) {
                savedOrder.setId(100L);
            }
            return savedOrder;
        });

        when(restTemplate.postForObject(anyString(), any(DeductStockRequest.class), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getOrderId());
        assertEquals("CONFIRMED", response.getStatus());
        verify(kafkaProducerService, times(1))
                .sendOrderEvent(eq("100"), eq("CONFIRMED"), anyString());
    }

    @Test
    @DisplayName("Debe rechazar la orden cuando el stock es insuficiente")
    void createOrder_Rejected_WhenStockIsInsufficient() {
        // Arrange
        OrderRequest request = buildSampleOrderRequest();

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            if (savedOrder.getId() == null) {
                savedOrder.setId(101L);
            }
            return savedOrder;
        });

        when(restTemplate.postForObject(anyString(), any(DeductStockRequest.class), eq(Boolean.class)))
                .thenReturn(Boolean.FALSE);

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals("Stock insuficiente", response.getRejectReason());
        verify(kafkaProducerService, times(1))
                .sendOrderEvent(eq("101"), eq("REJECTED"), anyString());
    }

    @Test
    @DisplayName("Debe ejecutar el Fallback y dejar la orden en PENDING si falla la llamada remota")
    void createOrderFallback_ShouldSetStatusToPending() {
        // Arrange
        OrderRequest request = buildSampleOrderRequest();
        RuntimeException cause = new RuntimeException("Connection Refused");

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            savedOrder.setId(102L);
            return savedOrder;
        });

        // Act
        OrderResponse response = orderService.createOrderFallback(request, cause);

        // Assert
        assertNotNull(response);
        assertEquals("PENDING", response.getStatus());
        assertTrue(response.getRejectReason().contains("Inventory Service no disponible"));
        verify(kafkaProducerService, times(1))
                .sendOrderEvent(eq("102"), eq("PENDING"), anyString());
    }

    private OrderRequest buildSampleOrderRequest() {
        OrderItemRequest item = OrderItemRequest.builder()
                .productCode("PROD-A100")
                .quantity(2)
                .build();

        return OrderRequest.builder()
                .customerId("CLI-1020")
                .items(List.of(item))
                .build();
    }
}