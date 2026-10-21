package com.workshop.orders.service;

import com.workshop.orders.model.CreateOrderRequest;
import com.workshop.orders.model.InvalidOrderException;
import com.workshop.orders.model.Order;
import com.workshop.orders.model.OrderNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class OrderService {

    private final Map<String, Order> store = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(1);

    public OrderService() {
        // Pre-seeded data so GET/DELETE work without a prior POST in the same execution
        store.put("ORD-001", new Order("ORD-001", "CUST-42", "MacBook Air", 1));
        store.put("ORD-002", new Order("ORD-002", "CUST-42", "Magic Mouse", 2));
        store.put("ORD-003", new Order("ORD-003", "CUST-7",  "Keyboard",    1));
        counter.set(4);
    }

    public Order createOrder(CreateOrderRequest req) {
        validate(req);
        String id = "ORD-" + String.format("%03d", counter.getAndIncrement());
        Order order = new Order(id, req.getCustomerId(), req.getProduct(), req.getQuantity());
        store.put(id, order);
        return order;
    }

    public Order getOrder(String id) {
        Order order = store.get(id);
        if (order == null) throw new OrderNotFoundException(id);
        return order;
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        return store.values().stream()
                .filter(o -> o.getCustomerId().equals(customerId))
                .collect(Collectors.toList());
    }

    public void deleteOrder(String id) {
        if (store.remove(id) == null) throw new OrderNotFoundException(id);
    }

    private void validate(CreateOrderRequest req) {
        if (req.getCustomerId() == null || req.getCustomerId().isBlank())
            throw new InvalidOrderException("customerId is required");
        if (req.getProduct() == null || req.getProduct().isBlank())
            throw new InvalidOrderException("product is required");
        if (req.getQuantity() <= 0)
            throw new InvalidOrderException("quantity must be greater than 0");
    }
}
