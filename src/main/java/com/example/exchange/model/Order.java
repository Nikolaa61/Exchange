package com.example.exchange.model;

import java.util.UUID;

public class Order {
    private final String id;
    private final double price;
    private final int amount;
    private final OrderType type;

    public Order(double price, int amount, OrderType type) {
        this.id = UUID.randomUUID().toString();
        this.price = price;
        this.amount = amount;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public double getPrice() {
        return price;
    }

    public int getAmount() {
        return amount;
    }

    public OrderType getType() {
        return type;
    }
}
