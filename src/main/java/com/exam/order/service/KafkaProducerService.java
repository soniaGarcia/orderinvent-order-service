package com.exam.order.service;

import com.exam.order.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendOrderEvent(String orderId, String status, String message) {
        String payload = String.format(
            "{\"orderId\": \"%s\", \"status\": \"%s\", \"message\": \"%s\"}", 
            orderId, status, message
        );
        log.info("Publicando evento a Kafka [Topic: {}] -> key: {}, payload: {}", 
                KafkaConfig.ORDER_EVENTS_TOPIC, orderId, payload);
        
        kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, orderId, payload)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Fallo al publicar mensaje en Kafka para el pedido {}", orderId, ex);
                } else {
                    log.debug("Evento publicado con éxito en offset: {}", result.getRecordMetadata().offset());
                }
            });
    }
}