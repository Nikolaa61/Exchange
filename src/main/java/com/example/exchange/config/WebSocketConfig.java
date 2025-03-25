package com.example.exchange.config;

import com.example.exchange.controller.OrderWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Autowired
    private OrderWebSocketHandler orderWebSocketHandler;

    @Bean
    public SimpleUrlHandlerMapping handlerMapping() {
        Map<String, WebSocketHandler> urlMap = Map.of("/orders-ws", orderWebSocketHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(10);
        mapping.setUrlMap(urlMap);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}