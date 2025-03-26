# Simple Exchange (TeleTrader Test)

This is a test project demonstrating a simple backend service for processing buy and sell orders, simulating a basic stock exchange. The project provides both REST and WebSocket interfaces for sending and receiving order-related data.

## Features

- **Language & Build Tool**: Java 17 + Maven  
- **Framework**: Spring Boot (WebFlux) – reactive, non-blocking architecture  
- **REST API** for creating new orders and retrieving the Top 10 buy/sell orders  
- **WebSocket endpoint** for interactive order submission and live match updates  
- **Asynchronous processing** using a blocking queue (`LinkedBlockingQueue`) and a worker thread pool (`ExecutorService`), combined with reactive wrappers  
- **Order Matching**: Automatically matches compatible BUY and SELL orders based on price, including partial matches

## Running the Project

### 1. Get the Code

The project is provided as a `.zip` file or can be cloned from a repository.

Open the project folder in your terminal or IDE.

### 2. Build the Project

In the root directory, run:

```bash
mvn clean install
```

This will download dependencies and compile the project.

### 3. Start the Application

Run with Maven:

```bash
mvn spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar target/exchange-0.0.1-SNAPSHOT.jar
```

The application will start on port `8080`.

## REST API Endpoints

### 1. Create Order

**POST** `/api/orders`

#### Request Body:

```json
{
  "price": 100.5,
  "amount": 10,
  "type": "BUY"
}
```

- `price`: the desired price  
- `amount`: quantity to buy or sell  
- `type`: `"BUY"` or `"SELL"`

#### Response:

Returns a JSON representation of the created order.

---

### 2. Top 10 Orders

**GET** `/api/orders/top`

#### Response:

```json
{
  "buyOrders": [
    {
      "price": 120.5,
      "amount": 12,
      "type": "BUY"
    }
  ],
  "sellOrders": [
    {
      "price": 80.0,
      "amount": 5,
      "type": "SELL"
    }
  ]
}
```

- `buyOrders`: sorted in descending order by price (highest first)  
- `sellOrders`: sorted in ascending order by price (lowest first)  
- Each list contains up to 10 entries

---

### 3. Match History

**GET** `/api/matches/all`  
Returns a list of all matched orders.

**GET** `/api/matches/latest`  
Returns the last 10 matched orders.

---

## WebSocket Endpoint

- **URI**: `ws://localhost:8080/orders-ws`

Once connected, the client can send JSON messages like:

### New Order:

```json
{
  "action": "NEW_ORDER",
  "payload": {
    "price": 101.0,
    "amount": 20,
    "type": "BUY"
  }
}
```

### Request Top Orders:

```json
{
  "action": "GET_TOP_ORDERS"
}
```

### Server Responses:

#### Order Accepted:

```json
{
  "action": "ORDER_ACCEPTED",
  "payload": {
    "id": "...",
    "price": 101.0,
    "amount": 20,
    "type": "BUY"
  }
}
```

#### Top Orders:

```json
{
  "action": "TOP_ORDERS",
  "payload": {
    "buyOrders": [...],
    "sellOrders": [...]
  }
}
```

#### Order Match (broadcast to all clients):

```json
{
  "action": "ORDER_MATCHED",
  "payload": {
    "buyPrice": ...,
    "sellPrice": ...,
    "amount": ...
  }
}
```

---

## Architecture Overview

### `OrderService`

- Maintains two `ConcurrentSkipListMap` structures for BUY and SELL orders (sorted by price)
- Incoming orders are placed in a `LinkedBlockingQueue` and processed by background workers
- Matching logic compares BUY orders against the lowest available SELL prices (and vice versa)
- Partial matches are supported — remaining quantities are reinserted into the order books

### `OrderController` and `MatchHistoryController`

- Provide REST endpoints for creating orders, retrieving the top 10 orders, and match history

### `OrderWebSocketHandler` and `WebSocketConfig`

- Define a WebSocket endpoint at `/orders-ws`
- Handle actions: `NEW_ORDER`, `GET_TOP_ORDERS`
- Manage WebSocket sessions and broadcast match updates to all connected clients

---

## Testing

Use the following `curl` commands to test:

### Create BUY Order:

```bash
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{"price": 100.0, "amount": 10, "type":"BUY"}'
```

### Get Top Orders:

```bash
curl -X GET http://localhost:8080/api/orders/top
```

### Get Last 10 Matches:

```bash
curl -X GET http://localhost:8080/api/matches/latest
```

You can also use a WebSocket client (e.g., [websocat](https://github.com/vi/websocat), browser extension, Postman, etc.) to connect to:

```
ws://localhost:8080/orders-ws
```

And send the appropriate JSON messages.

---

## Disclaimer

This is a test/demo project intended for evaluation purposes only. It is not optimized for production use and lacks proper security, authentication, and persistence layers.

---

## Further Improvements

To bring this prototype closer to a real-world exchange system, several enhancements could be implemented:

- **User management and authentication**: Introduce user accounts and secure API access using JWT or OAuth2.
- **Persistent storage**: Store orders and match history in a relational database (e.g., PostgreSQL) or in-memory datastore (e.g., Redis) for durability.
- **Advanced order types**: Support for market, limit, stop-loss, and fill-or-kill orders.
- **Order matching engine refinement**: Implement time-priority matching and performance optimization using high-throughput data structures or event sourcing.
- **Message queue integration**: Use Kafka or RabbitMQ for processing high volumes of streaming orders and broadcasting market data.
- **Front-end interface**: Create a real-time dashboard using React or Angular to visualize the order book, trades, and statistics.

These enhancements would require additional technologies such as:
- **Spring Security** for user roles and authentication
- **PostgreSQL or Redis** for data storage
- **Apache Kafka** or **RabbitMQ** for event-driven communication
- **React.js** or **Angular** for interactive front-end components

With these additions, the system could scale to support multi-user environments and resemble a minimal production-grade exchange platform.
