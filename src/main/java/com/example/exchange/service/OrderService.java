package com.example.exchange.service;

import com.example.exchange.controller.OrderWebSocketHandler;
import com.example.exchange.model.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.*;

import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

@Service
public class OrderService {

    private final List<MatchRecord> matchHistory = new CopyOnWriteArrayList<>();
    private ExecutorService executorService;

    // Za BUY naloge – key je cena, sortiramo od najveće ka manjoj (reverseOrder)
    private final ConcurrentSkipListMap<Double, Queue<Order>> buyOrders =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());

    // Za SELL naloge – key je cena, ascending (podrazumevano)
    private final ConcurrentSkipListMap<Double, Queue<Order>> sellOrders =
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
            List<PriceLevel> topBuys = getTopNByPriceLevels(buyOrders, 10);
            List<PriceLevel> topSells = getTopNByPriceLevels(sellOrders, 10);
            return new TopOrdersResponse(topBuys, topSells);
        });
    }

    private List<PriceLevel> getTopNByPriceLevels(ConcurrentSkipListMap<Double, Queue<Order>> map, int n) {
        List<PriceLevel> result = new ArrayList<>(n);
        int levels = 0;

        for (Map.Entry<Double, Queue<Order>> entry : map.entrySet()) {
            double price = entry.getKey();
            Queue<Order> orders = entry.getValue();

            if (orders.isEmpty()) {
                continue;
            }

            int totalAmount = orders.stream()
                    .mapToInt(Order::getAmount)
                    .sum();

            OrderType type = orders.peek().getType();

            result.add(new PriceLevel(price, totalAmount, type));

            levels++;
            if (levels >= n) break;
        }

        return result;
    }

    private void addToMap(ConcurrentSkipListMap<Double, Queue<Order>> map, Order order) {
        map.computeIfAbsent(order.getPrice(), k -> new ConcurrentLinkedQueue<>()).offer(order);
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
            Map.Entry<Double, Queue<Order>> bestSellEntry = sellOrders.firstEntry();
            double bestSellPrice = bestSellEntry.getKey();
            Queue<Order> sellQueue = bestSellEntry.getValue();

            Order sellOrder = sellQueue.peek();
            if (sellOrder == null) {
                sellOrders.remove(bestSellPrice);
                continue;
            }

            if (buyOrder.getPrice() >= sellOrder.getPrice()) {
                int matchedAmount = Math.min(buyOrder.getAmount(), sellOrder.getAmount());

                logMatch(buyOrder, sellOrder, matchedAmount);

                buyOrder = new Order(buyOrder.getPrice(), buyOrder.getAmount() - matchedAmount, buyOrder.getType());
                Order updatedSell = new Order(sellOrder.getPrice(), sellOrder.getAmount() - matchedAmount, sellOrder.getType());

                sellQueue.poll(); // ukloni stari
                if (updatedSell.getAmount() > 0) {
                    sellQueue.offer(updatedSell); // dodaj novi
                }

                cleanUpIfEmpty(sellOrders, bestSellPrice);

            } else {
                break;
            }
        }

        if (buyOrder.getAmount() > 0) {
            addToMap(buyOrders, buyOrder);
        }
    }

    private void matchSellOrder(Order sellOrder) {
        while (sellOrder.getAmount() > 0 && !buyOrders.isEmpty()) {
            Map.Entry<Double, Queue<Order>> bestBuyEntry = buyOrders.firstEntry();
            double bestBuyPrice = bestBuyEntry.getKey();
            Queue<Order> buyQueue = bestBuyEntry.getValue();

            Order buyOrder = buyQueue.peek();
            if (buyOrder == null) {
                buyOrders.remove(bestBuyPrice);
                continue;
            }

            if (buyOrder.getPrice() >= sellOrder.getPrice()) {
                int matchedAmount = Math.min(sellOrder.getAmount(), buyOrder.getAmount());

                logMatch(buyOrder, sellOrder, matchedAmount);

                sellOrder = new Order(sellOrder.getPrice(), sellOrder.getAmount() - matchedAmount, sellOrder.getType());
                Order updatedBuy = new Order(buyOrder.getPrice(), buyOrder.getAmount() - matchedAmount, buyOrder.getType());

                buyQueue.poll();
                if (updatedBuy.getAmount() > 0) {
                    buyQueue.offer(updatedBuy);
                }

                cleanUpIfEmpty(buyOrders, bestBuyPrice);

            } else {
                break;
            }
        }

        if (sellOrder.getAmount() > 0) {
            addToMap(sellOrders, sellOrder);
        }
    }

    private void cleanUpIfEmpty(ConcurrentSkipListMap<Double, Queue<Order>> map, double price) {
        Queue<Order> queue = map.get(price);
        if (queue != null && queue.isEmpty()) {
            map.remove(price);
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

//    @PostConstruct
//    public void startWorkers() {
//        logger.info("Pokrecem {} workers...", WORKER_COUNT);
//        for (int i = 0; i < WORKER_COUNT; i++) {
//            Thread worker = new Thread(() -> {
//                while (true) {
//                    try {
//                        Order order = orderQueue.take(); // čeka na red
//                        match(order);
//                        logger.debug("Obradjen nalog: {}", order);
//                        if (orderQueue.size() % 100 == 0) {
//                            logger.info("Queue size: {}", orderQueue.size());
//                        }
//                    } catch (Exception e) {
//                        logger.error("Worker greska", e);
//                    }
//                }
//            });
//            worker.setDaemon(true);
//            worker.setName("OrderWorker-" + i);
//            worker.start();
//        }
//    }

    @PostConstruct
    public void startWorkers() {
        logger.info("Pokrecem {} workers...", WORKER_COUNT);
        executorService = Executors.newFixedThreadPool(WORKER_COUNT);

        for (int i = 0; i < WORKER_COUNT; i++) {
            int finalI = i;
            executorService.submit(() -> {
                Thread.currentThread().setName("OrderWorker-" + finalI);
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Order order = orderQueue.take(); // čeka na red
                        match(order);
                        logger.debug("Obradjen nalog: {}", order);
                        if (orderQueue.size() % 100 == 0) {
                            logger.info("Queue size: {}", orderQueue.size());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // označi kao prekinutu
                        break;
                    } catch (Exception e) {
                        logger.error("Worker greska", e);
                    }
                }
            });
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Gasenje worker niti...");
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Niti nisu zavrsile na vreme – forsiram gasenje.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Prekid prilikom gasenja niti.", e);
        }
    }
}
