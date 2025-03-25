package com.example.exchange.controller;

import com.example.exchange.model.Order;
import com.example.exchange.model.OrderRequest;
import com.example.exchange.model.TopOrdersResponse;
import com.example.exchange.service.OrderService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderWebSocketHandler implements WebSocketHandler {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public OrderWebSocketHandler(OrderService orderService) {
        this.orderService = orderService;
        this.objectMapper = new ObjectMapper();
        this.orderService.setWebSocketHandler(this);
    }

    // Kada se nova konekcija uspostavi
    public void registerSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    // Kada se konekcija zatvori
    public void unregisterSession(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    // Šaljemo meč poruku svima
    public void broadcastMatch(double buyPrice, double sellPrice, int amount) {
        String json = String.format(
                "{\"action\":\"ORDER_MATCHED\",\"payload\":{\"buyPrice\":%f,\"sellPrice\":%f,\"amount\":%d}}",
                buyPrice, sellPrice, amount
        );

        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                session.send(Mono.just(session.textMessage(json))).subscribe();
            }
        });
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        registerSession(session); // dodato zbog broadcast-a

        Flux<WebSocketMessage> inputMessages = session.receive()
                .flatMap(message -> handleMessage(session, message))
                .doFinally(signal -> unregisterSession(session));

        return session.send(inputMessages);
    }

    private Mono<WebSocketMessage> handleMessage(WebSocketSession session, WebSocketMessage message) {
        String payload = message.getPayloadAsText(StandardCharsets.UTF_8);

        try {
            JsonNode root = objectMapper.readTree(payload);
            String action = root.get("action").asText();

            if ("NEW_ORDER".equalsIgnoreCase(action)) {
                JsonNode orderNode = root.get("payload");
                if (orderNode == null) {
                    return Mono.just(session.textMessage("{\"action\":\"ERROR\",\"payload\":\"Missing payload\"}"));
                }

                OrderRequest req = objectMapper.treeToValue(orderNode, OrderRequest.class);

                // Validacija
                if (req.getPrice() < 0 || req.getAmount() <= 0 || req.getType() == null) {
                    return Mono.just(session.textMessage("{\"action\":\"ERROR\",\"payload\":\"Invalid order data\"}"));
                }

                Order order = new Order(req.getPrice(), req.getAmount(), req.getType());

                return orderService.addOrder(order)
                        .map(savedOrder -> {
                            String json = String.format(
                                    "{\"action\":\"ORDER_ACCEPTED\",\"payload\":{\"id\":\"%s\",\"price\":%f,\"amount\":%d,\"type\":\"%s\"}}",
                                    savedOrder.getId(),
                                    savedOrder.getPrice(),
                                    savedOrder.getAmount(),
                                    savedOrder.getType().name()
                            );
                            return session.textMessage(json);
                        });

            } else if ("GET_TOP_ORDERS".equalsIgnoreCase(action)) {
                return orderService.getTopOrders()
                        .map(top -> {
                            try {
                                String json = objectMapper.writeValueAsString(
                                        Map.of(
                                                "action", "TOP_ORDERS",
                                                "payload", top
                                        )
                                );
                                return session.textMessage(json);
                            } catch (Exception e) {
                                return session.textMessage("{\"action\":\"ERROR\",\"payload\":\"Serialization error\"}");
                            }
                        });
            } else {
                return Mono.just(session.textMessage("{\"action\":\"ERROR\",\"payload\":\"Unknown action\"}"));
            }

        } catch (Exception e) {
            return Mono.just(session.textMessage("{\"action\":\"ERROR\",\"payload\":\"Invalid JSON format\"}"));
        }
    }

}