# Electronics Store Admin System

Full-stack e-commerce admin system for electronics retail, built with **Spring Boot + React + MySQL**.

This project focuses on practical store operations:
- Product and category management
- Inventory and stock movement tracking
- Cart, order, and voucher flows
- Financial transaction tracking (income/expense/refund)
- RBAC + JWT authentication
- Store settings management

---

## 1. Tech Stack

### Backend
- Java **26**
- Spring Boot **4.0.5**
- Spring Web MVC, Spring Data JPA, Spring Security (JWT Resource Server)
- MySQL (Hibernate `ddl-auto=update`)
- springdoc-openapi (`/swagger-ui/index.html`)

### Frontend
- React **19**
- Vite **8**
- Material UI **9**
- Axios + React Router DOM

---

## 2. Core Features

## 2.1 Authentication & Authorization
- JWT login: `POST /auth/login`
- Current user profile: `GET /me`
- RBAC with roles and permissions
- Route-level + method-level security (`SecurityConfig` + `@PreAuthorize`)

### Default roles
- `SUPER_ADMIN`
- `SALES`
- `WAREHOUSE`
- `CUSTOMER`
- `CONTENT`

### Default permissions
- `PRODUCT_VIEW`, `PRODUCT_CREATE`, `PRODUCT_UPDATE`, `PRODUCT_DELETE`
- `ORDER_VIEW_SELF`, `ORDER_VIEW_ALL`, `ORDER_CREATE`, `ORDER_MODIFY_STATUS`
- `CART_VIEW`, `CART_MODIFY`
- `USER_MANAGE`
- `STATS_VIEW`
- `MARKETING_MANAGE`

---

## 2.2 Product & Category Management
- Product CRUD (`/products`)
- Bulk import products
- Metadata backfill for storage location/attributes
- Category CRUD (`/categories`)
- Category attribute definitions per category (required flag + sort order)

---

## 2.3 Inventory Management
- Stock list + low-stock alerts
- Inventory ledger by SKU
- Inbound stock
- Manual product creation
- Outbound fulfillment by order
- Returns processing
- Stock adjustments
- Summary reports by date/category/attribute

> Inventory movements are persisted to `inventory_transactions`.

---

## 2.4 Cart, Orders & Voucher Engine

### Cart
- Add/remove items
- Cart summary
- Apply/remove voucher code

### Orders
- Create order from product + quantity (+ optional voucher code)
- Update status (`CONFIRMED` / `CANCELLED`)
- Voucher usage is consumed on order creation and released on cancellation

### Voucher rules
- Discount type: `PERCENT` or `FIXED`
- Minimum order amount
- Maximum discount cap
- Total quota
- Per-user limit
- Active time window
- Product-level scope (`eligibleProductIds`)
- Usage logs for auditing

Marketing endpoints:
- `GET /marketing/vouchers`
- `POST /marketing/vouchers`
- `PUT /marketing/vouchers/{id}`
- `GET /marketing/vouchers/{id}/usage-logs`
- `GET /marketing/vouchers/product-options?keyword=...`

Public validation endpoint:
- `GET /vouchers/validate`

---

## 2.5 Financial Transactions

Transaction module (`/transactions`) supports:
- List and filter transactions
- Summary (income, expense, net)
- Manual transaction creation
- Confirm flow (`PENDING -> SUCCESS`)
- Reverse flow (mark source as `REVERSED` and create reversal record)

Transaction types:
- `INCOME`
- `EXPENSE`
- `REFUND`

Transaction methods:
- `CASH`, `BANK_TRANSFER`, `CARD`, `E_WALLET`, `ORDER`, `OTHER`

Order integration:
- Create order => auto record `INCOME`
- Cancel confirmed order => auto record `REFUND`

---

## 2.6 Store Settings (4-field Form)

Settings module is intentionally simple for demo scope:
- Store name
- Phone
- Address
- Mouser API Key

Backend strategy:
- single-row record with `id = 1` in table `settings`
- APIs:
  - `GET /settings`
  - `PUT /settings`

---

## 2.7 Super Admin Data Sync (Mouser/Nexar)
- Mouser sync endpoint for importing components into DB
- Nexar SQL export endpoints for component data generation
- Restricted to `SUPER_ADMIN`

---

## 3. Architecture Notes (Important)

### Persisted in MySQL (JPA entities)
- Products, categories, category attributes, attribute values
- Inventory transactions
- Vouchers + voucher usage logs
- Financial transactions
- Store settings

### In-memory (runtime-only)
- `OrderService`: orders are stored in a `ConcurrentHashMap`
- `CartService`: carts and applied voucher code are stored in memory
- `AccountService`: users/credentials are managed by in-memory user store

> **Impact:** restarting backend clears carts/orders in memory. This is acceptable for current project scope but should be migrated to DB for production.

---

## 4. Project Structure

```text
ecomerce/
├─ src/main/java/com/example/ecomerce/
│  ├─ config/            # Security + JWT config
│  ├─ controller/        # REST controllers
│  ├─ models/            # JPA entities + domain models
│  ├─ repository/        # Spring Data repositories
│  ├─ security/          # Role/Permission enums
│  └─ service/           # Business logic
├─ src/main/resources/
│  └─ application.properties
└─ admin-ui/
   ├─ src/pages/         # Admin pages (Orders, Inventory, Vouchers, Transactions, Settings, ...)
   └─ package.json
```

---

## 5. Getting Started

## 5.1 Prerequisites
- JDK 26
- Maven Wrapper (included)
- Node.js 20+
- MySQL server

## 5.2 Backend Setup

1. Create database:
```sql
CREATE DATABASE ecomerce_db;
```

2. Configure DB and secrets in:
`src/main/resources/application.properties`

Current default points to:
```properties
spring.datasource.url=jdbc:mysql://localhost:4000/ecomerce_db?sslMode=DISABLED&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=123456
```

3. (Optional) Seed settings row:
```sql
INSERT INTO settings (id, store_name, phone, address, mouser_api_key)
VALUES (1, 'T-Group', '0123456789', 'TP.HCM', 'key-api-cua-ban')
ON DUPLICATE KEY UPDATE
  store_name = VALUES(store_name),
  phone = VALUES(phone),
  address = VALUES(address),
  mouser_api_key = VALUES(mouser_api_key);
```

4. Run backend:

Windows:
```bash
.\mvnw.cmd spring-boot:run
```

macOS/Linux:
```bash
./mvnw spring-boot:run
```

Backend base URL: `http://localhost:8080`

Swagger UI:
`http://localhost:8080/swagger-ui/index.html`

## 5.3 Frontend Setup

```bash
cd admin-ui
npm install
npm run dev
```

Frontend default URL: `http://localhost:5173`

To change API URL, set:
```bash
VITE_API_BASE_URL=http://localhost:8080
```

---

## 6. Default Accounts (Demo)

Defined in `AccountService`:

| Username | Password | Role |
|---|---|---|
| `superadmin` | `super123` | `SUPER_ADMIN` |
| `sales` | `sales123` | `SALES` |
| `warehouse` | `warehouse123` | `WAREHOUSE` |
| `customer` | `customer123` | `CUSTOMER` |
| `content` | `content123` | `CONTENT` |

> Change these credentials before any real deployment.

---

## 7. API Overview (by module)

- **Auth**: `/auth/login`, `/me`
- **Users**: `/users` (CRUD + enable/disable)
- **Products**: `/products`, `/products/bulk-import`, `/products/backfill-metadata`
- **Categories**: `/categories`, `/categories/{id}/attributes`
- **Inventory**: `/inventory/stocks`, `/inventory/low-stock`, `/inventory/inbound`, `/inventory/outbound`, `/inventory/returns`, `/inventory/adjustments`, ...
- **Cart**: `/cart`, `/cart/summary`, `/cart/voucher`
- **Orders**: `/orders`, `/orders/{orderId}/status`
- **Marketing/Vouchers**: `/marketing/vouchers`, `/marketing/vouchers/{id}/usage-logs`, `/marketing/vouchers/product-options`
- **Voucher Validation**: `/vouchers/validate`
- **Transactions**: `/transactions`, `/transactions/summary`, `/transactions/{id}/confirm`, `/transactions/{id}/reverse`
- **Settings**: `/settings`
- **Stats**: `/stats/products`
- **Admin Sync/Export**: `/api/admin/...` (SUPER_ADMIN only)

---

## 8. Frontend Pages (active routes)

- `/dashboard`
- `/orders`
- `/transactions`
- `/categories`
- `/products`
- `/inventory`
- `/customers`
- `/reviews`
- `/vouchers`
- `/staffs`
- `/roles-permissions`
- `/settings`
- `/admin-data-sync`
- `/profile`
- `/change-password`

---

## 9. Current Scope Notes

- Currency is currently shown as **USD (`$`)** in key money views (Orders/Transactions).
- Banner/Blog are not active in current routed admin workflow.
- The codebase is optimized for graduation-project demonstration and operation flow clarity.

---

## 10. Suggested Next Improvements

1. Persist orders/carts/users to MySQL (replace in-memory stores).
2. Add database migrations (Flyway/Liquibase) for versioned schema changes.
3. Add automated tests for services/controllers.
4. Centralize i18n and money formatting from system settings.
5. Add observability (structured logs, metrics, tracing).

