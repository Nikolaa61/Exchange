package com.example.exchange.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class OrderRequest {

    @Min(value = 0, message = "Price must be non-negative")
    private double price;

    @Min(value = 1, message = "Amount must be at least 1")
    private int amount;

    @NotNull(message = "Order type must be provided")
    private OrderType type;

    public OrderRequest() {
    }

    public OrderRequest(double price, int amount, OrderType type) {
        this.price = price;
        this.amount = amount;
        this.type = type;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }
}
