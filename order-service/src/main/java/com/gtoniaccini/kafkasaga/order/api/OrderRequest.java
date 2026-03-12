package com.gtoniaccini.kafkasaga.order.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderRequest(
        @NotBlank String productId,
        @Min(1) int quantity
) {
}
