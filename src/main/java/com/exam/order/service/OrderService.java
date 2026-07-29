package com.exam.order.service;

import com.exam.order.dto.DeductStockRequest;
import com.exam.order.dto.OrderRequest;
import com.exam.order.dto.OrderResponse;
import com.exam.order.exception.ResourceNotFoundException;
import com.exam.order.model.Order;
import com.exam.order.model.OrderStatus;
import com.exam.order.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaProducerService kafkaProducerService;
    private final RestTemplate restTemplate;

    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;

    @Transactional
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "createOrderFallback")
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creando pedido inicial para el cliente: {}", request.getCustomerId());

        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .status(OrderStatus.PENDING)
                .build();
        order = orderRepository.save(order);

        DeductStockRequest stockRequest = new DeductStockRequest(request.getProductId(), request.getQuantity());
        
        log.info("Consultando stock a Inventory Service: {}", inventoryServiceUrl + "/deduct");
        Boolean isStockDeducted = restTemplate.postForObject(
                inventoryServiceUrl + "/deduct", stockRequest, Boolean.class);

        if (Boolean.TRUE.equals(isStockDeducted)) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            log.info("Pedido {} CONFIRMADO.", order.getId());

            kafkaProducerService.sendOrderEvent(
                order.getId().toString(), 
                OrderStatus.CONFIRMED.name(), 
                "Pedido confirmado y stock descontado."
            );
        } else {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectReason("Stock insuficiente");
            orderRepository.save(order);
            log.warn("Pedido {} RECHAZADO por falta de stock.", order.getId());

            kafkaProducerService.sendOrderEvent(
                order.getId().toString(), 
                OrderStatus.REJECTED.name(), 
                "Stock insuficiente."
            );
        }

        return mapToResponse(order);
    }

    public OrderResponse createOrderFallback(OrderRequest request, Throwable t) {
        log.error("Circuit Breaker activo. Activando Fallback por error en comunicación con Inventario: {}", t.getMessage());

        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .status(OrderStatus.PENDING)
                .rejectReason("Inventory Service no disponible. Verificación diferida.")
                .build();
        order = orderRepository.save(order);

        kafkaProducerService.sendOrderEvent(
            order.getId().toString(), 
            OrderStatus.PENDING.name(), 
            "Pedido guardado en estado PENDING por falla remota."
        );

        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + id));
        return mapToResponse(order);
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .status(order.getStatus().name())
                .rejectReason(order.getRejectReason())
                .createdAt(order.getCreatedAt())
                .build();
    }
}