package com.exam.order.service;

import com.exam.order.dto.*;
import com.exam.order.exception.ResourceNotFoundException;
import com.exam.order.model.Order;
import com.exam.order.model.OrderItem;
import com.exam.order.model.OrderStatus;
import com.exam.order.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        if (request.getItems() != null) {
            for (OrderItemRequest itemReq : request.getItems()) {
                OrderItem item = OrderItem.builder()
                        .productCode(itemReq.getProductCode())
                        .quantity(itemReq.getQuantity())
                        .build();
                order.addItem(item);
            }
        }

        order = orderRepository.save(order);

        DeductStockRequest stockRequest = new DeductStockRequest(request.getItems());

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
                .status(OrderStatus.PENDING)
                .rejectReason("Inventory Service no disponible. Verificación diferida.")
                .items(new ArrayList<>())
                .build();

        if (request.getItems() != null) {
            for (OrderItemRequest itemReq : request.getItems()) {
                OrderItem item = OrderItem.builder()
                        .productCode(itemReq.getProductCode())
                        .quantity(itemReq.getQuantity())
                        .build();
                order.addItem(item);
            }
        }

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
        List<OrderItemResponse> itemResponses = order.getItems() != null ?
                order.getItems().stream()
                        .map(item -> OrderItemResponse.builder()
                                .id(item.getId())
                                .productCode(item.getProductCode())
                                .quantity(item.getQuantity())
                                .build())
                        .collect(Collectors.toList()) : Collections.emptyList();

        return OrderResponse.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .items(itemResponses)
                .status(order.getStatus().name())
                .rejectReason(order.getRejectReason())
                .createdAt(order.getCreatedAt())
                .build();
    }
}