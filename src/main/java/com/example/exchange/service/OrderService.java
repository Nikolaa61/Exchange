package com.example.exchange.service;

import com.example.exchange.controller.OrderWebSocketHandler;
import com.example.exchange.model.MatchRecord;
import com.example.exchange.model.Order;
import com.example.exchange.model.OrderType;
import com.example.exchange.model.TopOrdersResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.*;

import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final List<MatchRecord> matchHistory = new CopyOnWriteArrayList<>();

    // Za BUY naloge – key je cena, sortiramo od najveće ka manjoj (reverseOrder)
    private final ConcurrentSkipListMap<Double, List<Order>> buyOrders =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());

    // Za SELL naloge – key je cena, ascending (podrazumevano)
    private final ConcurrentSkipListMap<Double, List<Order>> sellOrders =
            new ConcurrentSkipListMap<>();

    private OrderWebSocketHandler webSocketHandler;

    private final BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>(10_000);
    private static final int WORKER_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * Dodaj novi nalog (asinkrono)
     * @param order
     * @return
     */
    public Mono<Order> addOrder(Order order) {
        return Mono.fromCallable(() -> {

            int attempts = 0;
            boolean added = false;

            while (!added) {
                added = orderQueue.offer(order, 500, TimeUnit.MILLISECONDS);
                attempts++;

                if (!added) {
                    logger.warn("Queue full – pokusaj {}/3 za nalog: {}", attempts, order);
                }
            }

            logger.info("Nalog dodat u red: {}", order);
            return order;
        }).subscribeOn(Schedulers.boundedElastic()); /// offer je izbacivao upozorenje za moguce izgladnjivanje i sa ovim mu kazemo da koristimo non-blocking okruzenje
    }

    /**
     * top 10 BUY i SELL naloga
     * @return
     */
    public Mono<TopOrdersResponse> getTopOrders() {
        return Mono.fromCallable(() -> {
            List<Order> topBuys = getTopN(buyOrders, 10);
            List<Order> topSells = getTopN(sellOrders, 10);
            return new TopOrdersResponse(topBuys, topSells);
        });
    }

    private void addToMap(ConcurrentSkipListMap<Double, List<Order>> map, Order order) {
        map.computeIfAbsent(order.getPrice(), k -> new ArrayList<>()).add(order);
    }

    /**
     * Prolazimo kroz mapu dok ne skupimo N naloga
     * @param map
     * @param n
     * @return
     */
    private List<Order> getTopN(ConcurrentSkipListMap<Double, List<Order>> map, int n) {
        List<Order> result = new ArrayList<>(n);
        for (Map.Entry<Double, List<Order>> entry : map.entrySet()) {
            for (Order o : entry.getValue()) {
                if (result.size() >= n) break;
                result.add(o);
            }
            if (result.size() >= n) break;
        }
        return result;
    }

    private void match(Order incomingOrder) {
        if (incomingOrder.getType() == OrderType.BUY) {
            matchBuyOrder(incomingOrder);
        } else {
            matchSellOrder(incomingOrder);
        }
    }

    private void matchBuyOrder(Order buyOrder) {
        while (buyOrder.getAmount() > 0 && !sellOrders.isEmpty()) {
            Map.Entry<Double, List<Order>> bestSellEntry = sellOrders.firstEntry();
            double bestSellPrice = bestSellEntry.getKey();

            // Moze li BUY da se upari s ovim SELL?
            if (buyOrder.getPrice() >= bestSellPrice) {
                List<Order> sellList = bestSellEntry.getValue();
                Order sellOrder = sellList.get(0);

                int matchedAmount = Math.min(buyOrder.getAmount(), sellOrder.getAmount());

                logMatch(buyOrder, sellOrder, matchedAmount);

                buyOrder = new Order(buyOrder.getPrice(), buyOrder.getAmount() - matchedAmount, buyOrder.getType());
                Order updatedSell = new Order(sellOrder.getPrice(), sellOrder.getAmount() - matchedAmount, sellOrder.getType());

                // Ažuriraj SELL listu
                sellList.remove(0);
                if (updatedSell.getAmount() > 0) {
                    sellList.add(0, updatedSell);
                }

                if (sellList.isEmpty()) {
                    sellOrders.remove(bestSellPrice);
                }
            } else {
                break; // Nema više pogodnih SELL-ova
            }
        }

        if (buyOrder.getAmount() > 0) {
            addToMap(buyOrders, buyOrder);
        }
    }

    private void matchSellOrder(Order sellOrder) {
        while (sellOrder.getAmount() > 0 && !buyOrders.isEmpty()) {
            Map.Entry<Double, List<Order>> bestBuyEntry = buyOrders.firstEntry();
            double bestBuyPrice = bestBuyEntry.getKey();

            if (bestBuyPrice >= sellOrder.getPrice()) {
                List<Order> buyList = bestBuyEntry.getValue();
                Order buyOrder = buyList.get(0);

                int matchedAmount = Math.min(sellOrder.getAmount(), buyOrder.getAmount());

                logMatch(buyOrder, sellOrder, matchedAmount);

                sellOrder = new Order(sellOrder.getPrice(), sellOrder.getAmount() - matchedAmount, sellOrder.getType());
                Order updatedBuy = new Order(buyOrder.getPrice(), buyOrder.getAmount() - matchedAmount, buyOrder.getType());

                // Ažuriraj BUY listu
                buyList.remove(0);
                if (updatedBuy.getAmount() > 0) {
                    buyList.add(0, updatedBuy);
                }

                if (buyList.isEmpty()) {
                    buyOrders.remove(bestBuyPrice);
                }
            } else {
                break; // Nema više pogodnih BUY-ova
            }
        }

        if (sellOrder.getAmount() > 0) {
            addToMap(sellOrders, sellOrder);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private void logMatch(Order buyOrder, Order sellOrder, int amount) {
        matchHistory.add(new MatchRecord(buyOrder.getPrice(), sellOrder.getPrice(), amount));

        logger.info("MATCHED: BUY [price={}, amount={}] <--> SELL [price={}, amount={}] | Executed amount: {}",
                buyOrder.getPrice(), buyOrder.getAmount(),
                sellOrder.getPrice(), sellOrder.getAmount(),
                amount
        );

        if (webSocketHandler != null) {
            webSocketHandler.broadcastMatch(buyOrder.getPrice(), sellOrder.getPrice(), amount);
        }
    }

    public void setWebSocketHandler(OrderWebSocketHandler handler) {
        this.webSocketHandler = handler;
    }

    @PostConstruct
    public void startWorkers() {
        logger.info("Pokrecem {} workers...", WORKER_COUNT);
        for (int i = 0; i < WORKER_COUNT; i++) {
            Thread worker = new Thread(() -> {
                while (true) {
                    try {
                        Order order = orderQueue.take(); // čeka na red
                        match(order);
                        logger.debug("Obradjen nalog: {}", order);
                        if (orderQueue.size() % 100 == 0) {
                            logger.info("Queue size: {}", orderQueue.size());
                        }
                    } catch (Exception e) {
                        logger.error("Worker greska", e);
                    }
                }
            });
            worker.setDaemon(true);
            worker.setName("OrderWorker-" + i);
            worker.start();
        }
    }

    public List<MatchRecord> getMatchHistory() {
        return new ArrayList<>(matchHistory);
    }

    public List<MatchRecord> getLatestMatches(int limit) {
        int total = matchHistory.size();
        if (limit >= total) {
            return new ArrayList<>(matchHistory);
        }
        return new ArrayList<>(matchHistory.subList(total - limit, total));
    }
}
