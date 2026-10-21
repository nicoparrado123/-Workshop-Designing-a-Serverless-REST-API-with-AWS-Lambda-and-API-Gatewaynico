package com.workshop.orders;

import com.workshop.orders.model.CreateOrderRequest;
import com.workshop.orders.model.InvalidOrderException;
import com.workshop.orders.model.Order;
import com.workshop.orders.model.OrderNotFoundException;
import com.workshop.orders.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService();
    }

    @Test
    void createOrder_validRequest_returnsOrderWithId() {
        Order order = service.createOrder(new CreateOrderRequest("CUST-99", "Laptop", 1));
        assertNotNull(order.getId());
        assertEquals("CUST-99", order.getCustomerId());
        assertEquals("Laptop", order.getProduct());
        assertEquals(1, order.getQuantity());
    }

    @Test
    void getOrder_existingId_returnsOrder() {
        Order order = service.getOrder("ORD-001");
        assertEquals("ORD-001", order.getId());
    }

    @Test
    void getOrder_nonExistingId_throwsOrderNotFoundException() {
        assertThrows(OrderNotFoundException.class, () -> service.getOrder("ORD-999"));
    }

    @Test
    void getOrdersByCustomer_returnsMatchingOrders() {
        List<Order> orders = service.getOrdersByCustomer("CUST-42");
        assertFalse(orders.isEmpty());
        orders.forEach(o -> assertEquals("CUST-42", o.getCustomerId()));
    }

    @Test
    void getOrdersByCustomer_unknownCustomer_returnsEmptyList() {
        List<Order> orders = service.getOrdersByCustomer("CUST-UNKNOWN");
        assertTrue(orders.isEmpty());
    }

    @Test
    void createOrder_missingCustomerId_throwsInvalidOrderException() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("", "Laptop", 1)));
    }

    @Test
    void createOrder_missingProduct_throwsInvalidOrderException() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-1", "", 1)));
    }

    @Test
    void createOrder_zeroQuantity_throwsInvalidOrderException() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-1", "Laptop", 0)));
    }

    @Test
    void deleteOrder_existingId_removesOrder() {
        service.deleteOrder("ORD-001");
        assertThrows(OrderNotFoundException.class, () -> service.getOrder("ORD-001"));
    }

    @Test
    void deleteOrder_nonExistingId_throwsOrderNotFoundException() {
        assertThrows(OrderNotFoundException.class, () -> service.deleteOrder("ORD-999"));
    }
}
