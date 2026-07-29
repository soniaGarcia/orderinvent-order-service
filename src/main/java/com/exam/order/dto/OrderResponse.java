package com.exam.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long orderId;
    private String customerId;
    private String productId;
    private Integer quantity;
    private String status;
    private String rejectReason;
    private LocalDateTime createdAt;
}