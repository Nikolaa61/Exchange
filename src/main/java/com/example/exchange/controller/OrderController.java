package com.example.exchange.controller;

import com.example.exchange.model.Order;
import com.example.exchange.model.OrderRequest;
import com.example.exchange.model.TopOrdersResponse;
import com.example.exchange.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     *
     * POST /api/orders
     * Prima JSON: { "price": 100.5, "amount": 10, "type": "BUY" }
     * @param requestMono
     * @return
     */
    @PostMapping
    public Mono<Void> createOrder(@RequestBody @Valid Mono<OrderRequest> requestMono) {
        return requestMono
                .map(req -> new Order(req.getPrice(), req.getAmount(), req.getType()))
                .flatMap(orderService::addOrder)
                .then();
    }

    /**
     * GET /api/orders/top
     * Vraća JSON sa buyOrders i sellOrders listama
     * @return
     */

    @GetMapping("/top")
    public Mono<TopOrdersResponse> getTopOrders() {
        return orderService.getTopOrders();
    }
}