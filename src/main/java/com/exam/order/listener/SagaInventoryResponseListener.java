package com.exam.order.listener;

import com.exam.order.dto.OrderEvent;
import com.exam.order.dto.OrderItemRequest;
import com.exam.order.model.Order;
import com.exam.order.model.OrderStatus;
import com.exam.order.repository.OrderRepository;
import com.exam.order.service.KafkaProducerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaInventoryResponseListener {

    private final OrderRepository orderRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory-events", groupId = "order-saga-group")
    @Transactional
    public void handleInventoryResponse(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            Long orderId = root.path("orderId").asLong();
            String status = root.path("status").asText();

            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.PENDIENTE) {
                return; // Ignorar si la orden ya fue procesada o no existe
            }

            // Convertir entidades de ítems a DTO para el evento final
            List<OrderItemRequest> itemRequests = order.getItems() != null ?
                    order.getItems().stream()
                            .map(item -> new OrderItemRequest(item.getProductCode(), item.getQuantity()))
                            .collect(Collectors.toList()) : Collections.emptyList();

            if ("INVENTORY_SUCCESS".equalsIgnoreCase(status)) {
                order.setStatus(OrderStatus.CONFIRMADO);
                orderRepository.save(order);

                // Notificar confirmación final usando el DTO OrderEvent
                kafkaProducerService.sendOrderEvent(OrderEvent.builder()
                        .orderId(order.getId().toString())
                        .status(OrderStatus.CONFIRMADO.name())
                        .message("Orden reconciliada y confirmada exitosamente tras recuperación del servicio de inventario.")
                        .items(itemRequests)
                        .build());

                log.info("Saga completada: Orden #{} actualizada a CONFIRMADO", orderId);

            } else {
                order.setStatus(OrderStatus.RECHAZADO);
                order.setRejectReason("Stock insuficiente durante reconciliación asíncrona");
                orderRepository.save(order);

                // Notificar rechazo usando el DTO OrderEvent
                kafkaProducerService.sendOrderEvent(OrderEvent.builder()
                        .orderId(order.getId().toString())
                        .status(OrderStatus.RECHAZADO.name())
                        .message("Orden rechazada por falta de stock durante reconciliación de Saga.")
                        .items(itemRequests)
                        .build());

                log.info("Saga completada: Orden #{} actualizada a RECHAZADO", orderId);
            }

        } catch (Exception e) {
            log.error("Error al procesar respuesta de inventario en order-service", e);
        }
    }
}