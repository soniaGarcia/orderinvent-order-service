package com.exam.order.listener;

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

            if ("INVENTORY_SUCCESS".equalsIgnoreCase(status)) {
                order.setStatus(OrderStatus.CONFIRMADO);
                orderRepository.save(order);

                // Notificar al tópico de notificaciones la confirmación final
                kafkaProducerService.sendOrderEvent(
                    order.getId().toString(),
                    OrderStatus.CONFIRMADO.name(),
                    "Orden reconciliada y confirmada exitosamente tras recuperación del servicio de inventario."
                );
                log.info("Saga completada: Orden #{} actualizada a CONFIRMADO", orderId);

            } else {
                order.setStatus(OrderStatus.RECHAZADO);
                order.setRejectReason("Stock insuficiente durante reconciliación asíncrona");
                orderRepository.save(order);

                kafkaProducerService.sendOrderEvent(
                    order.getId().toString(),
                    OrderStatus.RECHAZADO.name(),
                    "Orden rechazada por falta de stock durante reconciliación de Saga."
                );
                log.info("Saga completada: Orden #{} actualizada a RECHAZADO", orderId);
            }

        } catch (Exception e) {
            log.error("Error al procesar respuesta de inventario en order-service", e);
        }
    }
}