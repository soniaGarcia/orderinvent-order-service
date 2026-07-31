package com.exam.order.service;

import com.exam.order.config.KafkaConfig;
import com.exam.order.dto.OrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendOrderEvent(OrderEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info("Publicando evento a Kafka [Topic: {}] -> key: {}, payload: {}", 
                    KafkaConfig.ORDER_EVENTS_TOPIC, event.getOrderId(), payload);
            
            kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, event.getOrderId(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Fallo al publicar mensaje en Kafka para el pedido {}", event.getOrderId(), ex);
                    } else {
                        log.debug("Evento publicado con éxito en offset: {}", result.getRecordMetadata().offset());
                    }
                });
        } catch (Exception e) {
            log.error("Error al serializar el evento de la orden #{}", event.getOrderId(), e);
        }
    }
}