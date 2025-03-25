package com.example.exchange;

import com.example.exchange.model.Order;
import com.example.exchange.model.OrderType;
import com.example.exchange.model.MatchRecord;
import com.example.exchange.service.OrderService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

public class OrderServiceTest {

    @Test
    public void testMassiveOrderMatching() throws InterruptedException {
        OrderService service = new OrderService();

        // Rukujemo match broadcastima bez stvarnog WebSocket handlera
        service.setWebSocketHandler(null);

        int N = 20000;
        CountDownLatch latch = new CountDownLatch(N * 2); // BUY + SELL

        // Simulacija worker pokretanja (pošto nemamo Spring konteks ovde)
        new Thread(service::startWorkers).start();

        for (int i = 0; i < N; i++) {
            Order order = new Order(100.0, 1, OrderType.BUY);
            Mono<Order> result = service.addOrder(order);
            result.subscribe(o -> latch.countDown());
        }

        // SELL nalozi koji mogu odmah da se mecuju
        for (int i = 0; i < N; i++) {
            Order order = new Order(90.0, 1, OrderType.SELL); // jeftinije – poklapa se sa BUY 100
            Mono<Order> result = service.addOrder(order);
            result.subscribe(o -> latch.countDown());
        }

        // Da sacekamo da svi budu ubaceni
        latch.await();

        Thread.sleep(2000);

        List<MatchRecord> matches = service.getMatchHistory();
        System.out.println("Ukupno meceva: " + matches.size());

        assertEquals(N, matches.size(), "Treba da se izvrsi tacno " + N + " meceva");

        // Da li su cene ispravne
        for (MatchRecord m : matches) {
            assertTrue(m.getBuyPrice() >= m.getSellPrice());
            assertEquals(1, m.getAmount());
        }
    }
}