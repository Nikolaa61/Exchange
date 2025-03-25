package com.example.exchange.model;

import java.util.List;

public class TopOrdersResponse {
    private List<Order> buyOrders;
    private List<Order> sellOrders;

    public TopOrdersResponse() {
    }

    public TopOrdersResponse(List<Order> buyOrders, List<Order> sellOrders) {
        this.buyOrders = buyOrders;
        this.sellOrders = sellOrders;
    }

    public List<Order> getBuyOrders() {
        return buyOrders;
    }

    public void setBuyOrders(List<Order> buyOrders) {
        this.buyOrders = buyOrders;
    }

    public List<Order> getSellOrders() {
        return sellOrders;
    }

    public void setSellOrders(List<Order> sellOrders) {
        this.sellOrders = sellOrders;
    }
}
