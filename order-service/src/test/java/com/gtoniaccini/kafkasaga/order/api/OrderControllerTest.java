package com.gtoniaccini.kafkasaga.order.api;

import com.gtoniaccini.kafkasaga.order.domain.OrderStatus;
import com.gtoniaccini.kafkasaga.order.exception.OrderNotFoundException;
import com.gtoniaccini.kafkasaga.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderService service;

    @Test
    void createOrder_returns201() throws Exception {
        OrderResponse response = new OrderResponse(1L, "PROD-001", 2, OrderStatus.PENDING, LocalDateTime.now());
        when(service.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"PROD-001\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createOrder_withInvalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_returns200() throws Exception {
        OrderResponse response = new OrderResponse(1L, "PROD-001", 2, OrderStatus.CONFIRMED, LocalDateTime.now());
        when(service.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void getOrder_whenNotFound_returns404() throws Exception {
        when(service.findById(99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound());
    }
}
