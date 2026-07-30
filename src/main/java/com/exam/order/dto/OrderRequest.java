package com.exam.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotBlank(message = "El ID del cliente es obligatorio")
    private String customerId;

    @NotEmpty(message = "La lista de ítems no puede estar vacía")
    @Valid
    private List<OrderItemRequest> items;
}