# Orders API — Serverless REST API Workshop

## Demo Video

[Watch on YouTube](https://youtu.be/2ert-8tQjrs)

---

## Project Structure

```
orders-api/
├── pom.xml
├── events/
│   ├── post-order.json
│   ├── get-order-by-id.json
│   ├── get-orders-by-customer.json
│   └── delete-order.json
└── src/
    ├── main/java/com/workshop/orders/
    │   ├── model/
    │   │   ├── Order.java
    │   │   ├── CreateOrderRequest.java
    │   │   ├── OrderNotFoundException.java
    │   │   └── InvalidOrderException.java
    │   ├── service/
    │   │   └── OrderService.java
    │   └── handler/
    │       └── OrderHandler.java
    └── test/java/com/workshop/orders/
        └── OrderServiceTest.java
```

## How to run

```bash
cd orders-api
mvn test        # runs the 10 unit tests
mvn package     # builds target/orders-api-1.0-SNAPSHOT.jar
```

Lambda handler: `com.workshop.orders.handler.OrderHandler::handleRequest`

---

## Section 4 — API Design

The only resource is **Order**, exposed at `/orders`.

| Operation | Method | URL | Path Params | Query Params | Body | Response | Code |
|---|---|---|---|---|---|---|---|
| Create order | POST | /orders | — | — | `{"customerId","product","quantity"}` | Created order | 201 |
| Get order | GET | /orders/{id} | `id` | — | — | Order | 200 |
| Find by customer | GET | /orders | — | `customerId` | — | List of orders | 200 |
| Delete order | DELETE | /orders/{id} | `id` | — | — | empty | 204 |

All errors return:

```json
{
  "error": "ORDER_NOT_FOUND",
  "message": "Order ORD-999 does not exist"
}
```

| Case | Code |
|---|---|
| Order not found | 404 |
| Invalid input | 400 |
| Unexpected error | 500 |

**What are the resources in your API?**
Just `Order`. Accessible as a collection at `/orders` and as a single item at `/orders/{id}`.

**Why `/orders/{id}` to retrieve a specific order?**
The ID uniquely identifies one item in the collection. Putting it in the path is standard REST — the path represents where the resource lives in the hierarchy.

**Why is `customerId` a query parameter instead of a path parameter?**
A customer is not a sub-resource of an order, it's a filter on the collection. Query parameters are for filtering; path parameters are for identifying.

**What should POST /orders return on success?**
`201 Created` with the full order in the body and a `Location: /orders/{id}` header.

**What should the API return when an order doesn't exist?**
`404 Not Found` with `{"error":"ORDER_NOT_FOUND","message":"Order ORD-999 does not exist"}`.

**What should an error response contain?**
A machine-readable error code (`error`) and a human-readable message (`message`).

---

## Section 6 — Checkpoint 1

**Can the application be tested without AWS?**
Yes. `OrderService` and all model classes are plain Java. `mvn test` runs with no AWS credentials or network access needed.

**Does it need a web server?**
No. There's no embedded server. Lambda invokes the handler directly.

**Does it need Spring Boot?**
No. Spring Boot adds startup overhead that hurts Lambda cold start times.

**What does the generated JAR contain?**
A fat JAR with the application classes plus all dependencies (Jackson, aws-lambda-java-core, aws-lambda-java-events).

**Which parts know about HTTP?**
Only `OrderHandler`. `OrderService` and the models have no knowledge of HTTP, headers, or status codes.

---

## Section 7 — Checkpoint 2: how Lambda converts JSON

The Lambda Java runtime uses Jackson to deserialize the incoming JSON into the declared input type and to serialize the return value back to JSON. It uses the no-argument constructor and setters — standard Jackson behavior.

```
Incoming JSON
     │
     ▼
Lambda Runtime (Jackson deserializes)
     │
     ▼
APIGatewayProxyRequestEvent
     │
     ▼
OrderHandler.handleRequest(event, context)
     │
     ▼
APIGatewayProxyResponseEvent
     │
     ▼
Lambda Runtime (Jackson serializes)
     │
     ▼
JSON response
```

---

## Section 10 — Checkpoint 3: responsibilities with custom integration (Architecture A)

With custom (non-proxy) integration, API Gateway handles:

- Routing the HTTP request to the right Lambda function
- Extracting path parameters from the URL template
- Extracting query parameters
- Transforming the HTTP request into the JSON Lambda receives (via VTL mapping templates)
- Transforming the Lambda response into an HTTP response (status, headers, body)
- Mapping Lambda errors to HTTP status codes via regex

**Advantages:**
- Lambda receives clean objects with no HTTP noise
- Business logic is completely decoupled from HTTP

**Disadvantages:**
- VTL templates are verbose and can't be unit tested locally
- Every new operation needs new API Gateway configuration
- Error mapping via regex is fragile
- The API contract is split between Java code and API Gateway config

---

## Section 14 — Architecture Comparison

| Question | Architecture A (custom) | Architecture B (proxy) |
|---|---|---|
| More API Gateway config? | Yes — VTL per operation | No — one proxy resource covers everything |
| More Java infrastructure code? | No | Yes — `OrderHandler` parses method, path, params |
| Easier to test locally? | No — VTL can't be unit tested | Yes — `OrderHandler` is testable with plain Java objects |
| Where are status codes defined? | In API Gateway | In `OrderHandler` |
| Scales to 30 operations? | Poorly — 30 VTL templates | Fine — routing stays in Java |
| Cleaner business logic? | Yes | Yes — `OrderService` still doesn't see HTTP |
| Easier for another developer? | No — requires knowing VTL | Yes — everything is in Java |
| Which would you pick for a real system? | No | **Yes** |

Architecture B is the better choice for anything beyond a prototype. Routing and HTTP handling live in Java where they can be versioned, tested, and understood without opening the AWS console. The only trade-off is that `OrderHandler` knows about `APIGatewayProxyRequestEvent`, but that's contained in one class.

---

## Section 16 — AI Assistant Decision Analysis

### Decision 1: one Lambda for all operations

The agent proposed a single `OrderHandler` with an if/else router for all 4 routes.

It proposed this because it's simpler to deploy — one JAR, one Lambda, one API Gateway integration.

I agree at this scale. With only 4 operations sharing the same `OrderService`, splitting into 4 Lambdas would add complexity with no real benefit.

The alternative would be one Lambda per operation (`CreateOrderHandler`, `GetOrderHandler`, etc.), which allows independent scaling and deployment but multiplies configuration.

I went with the single handler. The routing is trivial and the service layer is shared.

---

### Decision 2: validation inside `OrderService`, not in the handler

The agent proposed throwing `InvalidOrderException` from `OrderService.validate()` and catching it in `OrderHandler` to map it to 400.

It proposed this to keep business rules close to the domain. The service enforces its own invariants regardless of how it's called.

I agree. Validating that `quantity > 0` or that `customerId` isn't blank is a domain rule, not an HTTP rule. If the service were called from a batch job, the validation would still work.

The alternative would be validating in the handler before calling the service, but that mixes responsibilities and duplicates logic.

I put validation in `OrderService` and HTTP error mapping in `OrderHandler`.

---

### Decision 3: pre-seeded in-memory data

The agent proposed a `ConcurrentHashMap` initialized with sample orders so GET and DELETE work without needing a prior POST.

It proposed this because Lambda execution environments are ephemeral — data doesn't persist between invocations. Pre-seeding makes the API demonstrable without a database.

I agree for a workshop. The spec explicitly says a database isn't required and that in-memory data shouldn't be considered persistent.

The real alternative would be DynamoDB, which would be the right call in production, but that's outside the scope here.

I implemented the in-memory map with pre-seeded data and a comment in the code noting it's not persistent storage.

---

## Section 17 — DELETE prediction (written before implementation)

- Classes that need to change: `OrderService` (add `deleteOrder`) and `OrderHandler` (add DELETE branch)
- New Lambda needed? No, the handler already routes by HTTP method
- API Gateway changes? Yes, add a DELETE route — though the proxy resource already covers it
- Where does the deletion logic go? In `OrderService.deleteOrder()`
- Where does the 204 vs 404 decision live? `OrderService` throws `OrderNotFoundException` if the order doesn't exist. `OrderHandler` catches it and returns 404. On success, it returns 204. HTTP decision in the handler, existence check in the service.
- Tests to add: `deleteOrder_existingId_removesOrder` and `deleteOrder_nonExistingId_throwsOrderNotFoundException`

The agent proposed exactly the same thing. No surprises.

---

## Section 19 — Final Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                          AWS Cloud                              │
│                                                                 │
│   Client (curl / Postman)                                       │
│       │                                                         │
│       │  HTTP  (POST /orders, GET /orders/{id}, etc.)           │
│       ▼                                                         │
│   ┌─────────────────────────────┐                               │
│   │     Amazon API Gateway      │  ← AWS infrastructure        │
│   │  (HTTP API, proxy resource) │                               │
│   └──────────────┬──────────────┘                               │
│                  │  APIGatewayProxyRequestEvent (JSON)           │
│                  ▼                                               │
│   ┌─────────────────────────────┐                               │
│   │       AWS Lambda            │  ← AWS infrastructure        │
│   │  Runtime (Jackson ↔ JSON)   │                               │
│   │  ┌───────────────────────┐  │                               │
│   │  │    OrderHandler       │  │  ← HTTP adapter (Java)       │
│   │  │  - routes by method   │  │                               │
│   │  │  - extracts params    │  │                               │
│   │  │  - builds response    │  │                               │
│   │  └──────────┬────────────┘  │                               │
│   │             │               │                               │
│   │  ┌──────────▼────────────┐  │                               │
│   │  │    OrderService       │  │  ← plain Java (no AWS)       │
│   │  │  - createOrder()      │  │                               │
│   │  │  - getOrder()         │  │                               │
│   │  │  - getOrdersByCustomer│  │                               │
│   │  │  - deleteOrder()      │  │                               │
│   │  └───────────────────────┘  │                               │
│   └─────────────────────────────┘                               │
└─────────────────────────────────────────────────────────────────┘

No AWS dependency: Order, CreateOrderRequest,
  OrderNotFoundException, InvalidOrderException, OrderService

AWS dependency: OrderHandler (APIGatewayProxyRequestEvent,
  APIGatewayProxyResponseEvent, RequestHandler)
```

---

## Section 21 — Demo Video

[Watch on YouTube](https://youtu.be/2ert-8tQjrs)

**1. `mvn test`** — all 10 tests in `OrderServiceTest` pass with no AWS needed:
- create valid order
- get existing order
- get non-existing order throws exception
- find orders by customer
- customer with no orders returns empty list
- empty customerId throws exception
- empty product throws exception
- quantity 0 throws exception
- delete existing order removes it
- delete non-existing order throws exception

**2. `mvn package`** — builds `target/orders-api-1.0-SNAPSHOT.jar` (3.4 MB).

**3. Direct Lambda invocation** — from the AWS console Test tab using `get-order-by-id.json`, returns 200 with ORD-001 data.

**4. Live API calls to `https://9esztayyo6.execute-api.us-east-1.amazonaws.com`:**

| Call | Result |
|---|---|
| `GET /orders/ORD-002` | 200 — returns the order |
| `GET /orders?customerId=CUST-42` | 200 — returns array of orders |
| `POST /orders` valid body | 201 — returns created order |
| `GET /orders/ORD-999` | 404 — ORDER_NOT_FOUND |
| `POST /orders` empty customerId | 400 — INVALID_INPUT |
| `DELETE /orders/ORD-003` | 204 — no body |
| `DELETE /orders/ORD-999` | 404 — ORDER_NOT_FOUND |

---

## Test commands (PowerShell)

```powershell
$BASE = "https://9esztayyo6.execute-api.us-east-1.amazonaws.com"

# GET 200
Invoke-WebRequest -Uri "$BASE/orders/ORD-002" -Method GET | Select-Object -ExpandProperty Content

# GET by customer 200
Invoke-WebRequest -Uri "$BASE/orders?customerId=CUST-42" -Method GET | Select-Object -ExpandProperty Content

# POST 201
Invoke-WebRequest -Uri "$BASE/orders" -Method POST -ContentType "application/json" -Body '{"customerId":"CUST-99","product":"Laptop","quantity":2}' | Select-Object -ExpandProperty Content

# GET 404
Invoke-WebRequest -Uri "$BASE/orders/ORD-999" -Method GET | Select-Object -ExpandProperty Content

# POST 400
Invoke-WebRequest -Uri "$BASE/orders" -Method POST -ContentType "application/json" -Body '{"customerId":"","product":"Laptop","quantity":1}' | Select-Object -ExpandProperty Content

# DELETE 204
Invoke-WebRequest -Uri "$BASE/orders/ORD-003" -Method DELETE | Select-Object -ExpandProperty Content

# DELETE 404
Invoke-WebRequest -Uri "$BASE/orders/ORD-999" -Method DELETE | Select-Object -ExpandProperty Content
```

## API Gateway Configuration

1. Create an HTTP API in API Gateway.
2. Add a Lambda integration pointing to `orders-api`.
3. Add routes:
   - `POST /orders`
   - `GET /orders`
   - `GET /orders/{id}`
   - `DELETE /orders/{id}`
4. All routes use the same Lambda proxy integration with payload format `1.0`.
5. Deploy to the `$default` stage.
