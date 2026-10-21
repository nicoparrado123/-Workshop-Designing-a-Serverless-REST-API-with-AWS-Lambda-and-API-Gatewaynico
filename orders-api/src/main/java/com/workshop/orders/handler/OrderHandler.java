package com.workshop.orders.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.orders.model.CreateOrderRequest;
import com.workshop.orders.model.InvalidOrderException;
import com.workshop.orders.model.OrderNotFoundException;
import com.workshop.orders.service.OrderService;

import java.util.Map;

public class OrderHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final OrderService service = new OrderService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String method = event.getHttpMethod();
            String path   = event.getPath();

            if ("POST".equals(method) && "/orders".equals(path)) {
                return handleCreate(event);
            }
            if ("GET".equals(method) && path.matches("/orders/[^/]+")) {
                return handleGetById(event);
            }
            if ("GET".equals(method) && "/orders".equals(path)) {
                return handleGetByCustomer(event);
            }
            if ("DELETE".equals(method) && path.matches("/orders/[^/]+")) {
                return handleDelete(event);
            }
            return response(404, error("NOT_FOUND", "Route not found"));

        } catch (InvalidOrderException e) {
            return response(400, error("INVALID_INPUT", e.getMessage()));
        } catch (OrderNotFoundException e) {
            return response(404, error("ORDER_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return response(500, error("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    private APIGatewayProxyResponseEvent handleCreate(APIGatewayProxyRequestEvent event) throws Exception {
        CreateOrderRequest req = mapper.readValue(event.getBody(), CreateOrderRequest.class);
        var order = service.createOrder(req);
        var resp = response(201, mapper.writeValueAsString(order));
        resp.setHeaders(Map.of("Location", "/orders/" + order.getId()));
        return resp;
    }

    private APIGatewayProxyResponseEvent handleGetById(APIGatewayProxyRequestEvent event) throws Exception {
        String id = event.getPathParameters().get("id");
        return response(200, mapper.writeValueAsString(service.getOrder(id)));
    }

    private APIGatewayProxyResponseEvent handleGetByCustomer(APIGatewayProxyRequestEvent event) throws Exception {
        String customerId = event.getQueryStringParameters() == null ? null
                : event.getQueryStringParameters().get("customerId");
        if (customerId == null || customerId.isBlank())
            return response(400, error("INVALID_INPUT", "customerId query parameter is required"));
        return response(200, mapper.writeValueAsString(service.getOrdersByCustomer(customerId)));
    }

    private APIGatewayProxyResponseEvent handleDelete(APIGatewayProxyRequestEvent event) {
        String id = event.getPathParameters().get("id");
        service.deleteOrder(id);
        return response(204, "");
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body);
    }

    private String error(String code, String message) {
        try {
            return mapper.writeValueAsString(Map.of("error", code, "message", message));
        } catch (Exception e) {
            return "{\"error\":\"INTERNAL_ERROR\"}";
        }
    }
}
