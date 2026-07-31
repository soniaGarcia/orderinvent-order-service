package com.exam.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderEvent {
    private String orderId;
    private String status;
    private String message;
    private List<OrderItemRequest> items; // <-- CRUCIAL PARA LA RECONCILIACIÓN
}