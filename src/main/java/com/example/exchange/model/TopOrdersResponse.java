package com.example.exchange.model;

import java.util.List;

public class TopOrdersResponse {
    private List<PriceLevel> buyOrders;
    private List<PriceLevel> sellOrders;

    public TopOrdersResponse(List<PriceLevel> buyOrders, List<PriceLevel> sellOrders) {
        this.buyOrders = buyOrders;
        this.sellOrders = sellOrders;
    }

    public List<PriceLevel> getBuyOrders() {
        return buyOrders;
    }

    public List<PriceLevel> getSellOrders() {
        return sellOrders;
    }
}
