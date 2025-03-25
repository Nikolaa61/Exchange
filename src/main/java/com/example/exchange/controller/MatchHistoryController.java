package com.example.exchange.controller;

import com.example.exchange.model.MatchRecord;
import com.example.exchange.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchHistoryController {

    private final OrderService orderService;

    public MatchHistoryController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping(path = "/all")
    public List<MatchRecord> getAllMatches() {
        return orderService.getMatchHistory();
    }

    @GetMapping(path = "/latest")
    public List<MatchRecord> getLatestMatches() {
        return orderService.getLatestMatches(10);
    }
}