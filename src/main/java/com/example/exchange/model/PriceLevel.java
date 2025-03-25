package com.example.exchange.model;

public class PriceLevel {
    private double price;
    private int totalAmount;
    private OrderType type;

    public PriceLevel(double price, int totalAmount, OrderType type) {
        this.price = price;
        this.totalAmount = totalAmount;
        this.type = type;
    }

    public double getPrice() {
        return price;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public OrderType getType() {
        return type;
    }
}
