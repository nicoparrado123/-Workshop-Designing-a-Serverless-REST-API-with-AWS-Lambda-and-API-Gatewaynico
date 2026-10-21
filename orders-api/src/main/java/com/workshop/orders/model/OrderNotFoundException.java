package com.workshop.orders.model;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("Order " + orderId + " does not exist");
    }
}
