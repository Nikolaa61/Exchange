package com.example.exchange.model;

public class MatchRecord {
    private final double buyPrice;
    private final double sellPrice;
    private final int amount;

    public MatchRecord(double buyPrice, double sellPrice, int amount) {
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.amount = amount;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public int getAmount() {
        return amount;
    }
}
