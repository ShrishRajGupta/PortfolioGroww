# Stocks Portfolio Application

This is a Spring Boot-based application designed to manage user stock portfolios. It provides RESTful APIs to manage users, stocks, and trades, including features like portfolio calculation and randomization of stock prices.

---

## Installation Steps

### Prerequisites
- Java 17 or later
- Maven 3.6+
- MySQL Server
- IDE (e.g., IntelliJ IDEA, Eclipse)

### Steps
1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd stocks-portfolio-application
   ```

2. Set up the database:
    - Create a MySQL database named `stocks_portfolio` (or configure `application.properties`/`application.yml` for your database).
    - Run the following script to create the schema (if required):
      ```sql
      CREATE DATABASE stocks_portfolio;
      ```

3. Configure the application:
    - Update the `application.properties` or `application.yml` file with your database credentials:
      ```properties
      spring.datasource.url=jdbc:mysql://localhost:3306/stocks_portfolio
      spring.datasource.username=your_username
      spring.datasource.password=your_password
      spring.jpa.hibernate.ddl-auto=update
      ```

4. Build the application:
   ```bash
   mvn clean install
   ```

5. Run the application:
   ```bash
   mvn spring-boot:run
   ```

6. Access the APIs:
    - Base URL: `http://localhost:8080/api`

---

## API Endpoints


### 1. Record Trade
**Endpoint:** `POST /api/trade`

**Description:** Records a trade for a user and stock.

**Request Body:**
```json
{
  "userAccountId": 1,
  "stockId": 5,
  "tradeType": "BUY",
  "quantity": 10
}
```

**Response:**
```json
{
  "status": "SUCCESS",
  "message": "Trade recorded successfully."
}
```

---

### 2. Get Portfolio
**Endpoint:** `GET /api/portfolio/{userId}`

**Description:** Retrieves the portfolio details for a specific user.

**Response:**
```json
{
  "holdings": [
    {
      "stockName": "Stock1",
      "stockId": 1,
      "quantity": 10,
      "buyPrice": 100.0,
      "currentPrice": 105.0,
      "gainLoss": 50.0
    }
  ],
  "totalHoldingValue": 1050.0,
  "totalBuyPrice": 1000.0,
  "totalPL": 50.0,
  "totalPLPercentage": 5.0
}
```

---

### 3. Get Stock by ID
**Endpoint:** `GET /api/stocks/{stock_id}`

**Description:** Retrieves stock details for the specified stock ID.

**Response:**
```json
{
  "id": 5,
  "name": "Stock5",
  "openPrice": 105.0,
  "closePrice": 110.0,
  "highPrice": 115.0,
  "lowPrice": 100.0,
  "settlementPrice": 107.5
}
```

---

### 4. Update Stocks from CSV
**Endpoint:** `POST /api/stocks/update`

**Description:** Updates stock data by processing a CSV file.

**Request:**
- Form-Data with a file key containing the CSV file.

**Response:**
```json
"Stocks updated successfully."
```


### 5. Populate Users
**Endpoint:** `GET /api/populate/users`

**Description:** Adds 10 dummy user accounts to the database.

**Response:**
```json
"10 users added successfully."
```

---

### 6. Populate Stocks
**Endpoint:** `GET /api/populate/stocks`

**Description:** Adds 10 dummy stocks with predefined price ranges to the database.

**Response:**
```json
"10 stocks added successfully."
```

---

### 7. Populate Trades
**Endpoint:** `GET /api/populate/trades`

**Description:** Creates random trades for all users and stocks, with random trade types (`BUY` or `SELL`), quantities, and prices.

**Response:**
```json
"Trades with random data added for all users and stocks."
```

---

### 8. Randomize Stock Prices
**Endpoint:** `PUT /api/populate/stocks/update-prices`

**Description:** Updates stock prices (open, close, high, low, and settlement) to random values for all stocks in the database.

**Response:**
```json
"Stock prices randomized successfully."
```
---

## Repository Classes

### UserAccountRepository
**Description:** Handles CRUD operations for `UserAccount` entities.

**Methods:**
- `Optional<UserAccount> findByEmail(String email);`

---

### StockRepository
**Description:** Handles CRUD operations for `Stock` entities.

**Methods:**
- `Optional<Stock> findByName(String name);`

---

### TradeRepository
**Description:** Handles CRUD operations for `Trade` entities.

**Methods:**
- `List<Trade> findByUserAccountId(Long userAccountId);`
- `List<Trade> findByStockId(Long stockId);`

---

## Model Classes

### UserAccount
**Fields:**
- `Long id`
- `String name`
- `String email`
- `LocalDateTime createdAt`

---

### Stock
**Fields:**
- `Long id`
- `String name`
- `Double openPrice`
- `Double closePrice`
- `Double highPrice`
- `Double lowPrice`
- `Double settlementPrice`

---

### Trade
**Fields:**
- `Long id`
- `UserAccount userAccount`
- `Stock stock`
- `String tradeType`
- `Integer quantity`
- `Double price`
- `LocalDateTime createdAt`

---

## Additional Notes
- **Database Schema:** The database schema is managed automatically by Hibernate.
- **Testing:** Use Postman or similar tools to test the endpoints.
- **Default Ports:** The application runs on port `8080` by default. You can change this in `application.yaml`.

---

## Contact
For any issues, feel free to reach out via email at `shrishrg@gmail.com` or create an issue in the repository.


