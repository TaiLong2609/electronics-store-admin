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

---

## 11. Bản đồ file chi tiết (để quay lại dự án vẫn nắm nhanh)

Phần này mô tả vai trò từng file quan trọng theo đúng luồng đang chạy thực tế.

### 11.1 Backend - lõi bảo mật, đăng nhập, phân quyền

| File | Chức năng chính | Ghi chú khi bảo trì |
|---|---|---|
| `src/main/java/com/example/ecomerce/EcomerceApplication.java` | Entry point chạy Spring Boot | File main, hầu như không đổi nghiệp vụ |
| `src/main/java/com/example/ecomerce/config/SecurityConfig.java` | Khai báo toàn bộ rule quyền cho API, JWT decoder/encoder, CORS | Nếu API bị 403/401, kiểm tra file này đầu tiên |
| `src/main/java/com/example/ecomerce/config/JacksonConfig.java` | Cấu hình JSON mapper cho backend | Dùng khi cần chỉnh format serialize/deserialize |
| `src/main/java/com/example/ecomerce/controller/AuthController.java` | API login và lấy thông tin user hiện tại (`/auth/login`, `/me`) | Điểm vào xác thực của frontend |
| `src/main/java/com/example/ecomerce/service/AccountService.java` | Quản lý tài khoản in-memory, role, permission, enable/disable user | Tài khoản demo đang nằm ở đây (chưa lưu DB) |

### 11.2 Backend - đơn hàng, voucher, giao dịch tiền

| File | Chức năng chính | Ghi chú khi bảo trì |
|---|---|---|
| `src/main/java/com/example/ecomerce/controller/OrderController.java` | API tạo đơn, xem đơn, đổi trạng thái đơn | Chuyển actor vào service để log giao dịch đúng user |
| `src/main/java/com/example/ecomerce/service/OrderService.java` | Nghiệp vụ order: giữ tồn, áp voucher, hủy đơn, hoàn giữ tồn | Đang lưu order trong `ConcurrentHashMap` (in-memory) |
| `src/main/java/com/example/ecomerce/models/Order.java` | Model dữ liệu đơn hàng | Chứa tổng tiền, giảm giá, payableAmount |
| `src/main/java/com/example/ecomerce/controller/MarketingController.java` | API quản trị voucher CRUD + usage logs + product options | Cần quyền `MARKETING_MANAGE` |
| `src/main/java/com/example/ecomerce/controller/VoucherController.java` | API public/checkout để validate voucher (`/vouchers/validate`) | Dùng khi áp mã ở cart/order |
| `src/main/java/com/example/ecomerce/service/VoucherService.java` | Luật voucher: min order, quota, per-user, thời gian, product scope | Nơi xử lý consume/release voucher usage |
| `src/main/java/com/example/ecomerce/models/Voucher.java` | Entity voucher | Có trường `eligibleProductIds` để scope theo sản phẩm |
| `src/main/java/com/example/ecomerce/models/VoucherUsageLog.java` | Entity log dùng voucher | Track trạng thái USED/RELEASED |
| `src/main/java/com/example/ecomerce/models/VoucherDiscountType.java` | Enum loại giảm (`PERCENT`, `FIXED`) | Không nên hardcode string ở nơi khác |
| `src/main/java/com/example/ecomerce/models/VoucherUsageStatus.java` | Enum trạng thái log voucher | Dùng để audit lifecycle voucher |
| `src/main/java/com/example/ecomerce/repository/VoucherRepository.java` | Repository voucher | Truy vấn voucher theo code/ràng buộc |
| `src/main/java/com/example/ecomerce/repository/VoucherUsageLogRepository.java` | Repository usage logs | Dùng cho màn usage logs/admin thống kê |
| `src/main/java/com/example/ecomerce/controller/TransactionController.java` | API giao dịch tiền: list/filter, summary, create, confirm, reverse | Toàn bộ lỗi validate trả về message rõ cho UI |
| `src/main/java/com/example/ecomerce/service/TransactionService.java` | Core nghiệp vụ giao dịch tài chính + liên kết đơn hàng | Tạo INCOME khi tạo đơn, REFUND khi hủy đơn |
| `src/main/java/com/example/ecomerce/models/FinancialTransaction.java` | Entity `financial_transactions` | Có `relatedTransactionId` cho luồng reverse |
| `src/main/java/com/example/ecomerce/models/TransactionType.java` | Enum loại giao dịch (`INCOME`, `EXPENSE`, `REFUND`) | Dùng xuyên suốt backend + frontend |
| `src/main/java/com/example/ecomerce/models/TransactionStatus.java` | Enum trạng thái (`PENDING`, `SUCCESS`, `REVERSED`) | Summary mặc định tính SUCCESS nếu không filter status |
| `src/main/java/com/example/ecomerce/models/TransactionMethod.java` | Enum phương thức (`CASH`, `BANK_TRANSFER`, `CARD`, `E_WALLET`, `ORDER`, `OTHER`) | `ORDER` dùng cho giao dịch auto từ đơn |
| `src/main/java/com/example/ecomerce/repository/FinancialTransactionRepository.java` | Repository giao dịch tài chính | Có check giao dịch đã bị reverse chưa |

### 11.3 Backend - cấu hình cửa hàng, kho và đồng bộ dữ liệu

| File | Chức năng chính | Ghi chú khi bảo trì |
|---|---|---|
| `src/main/java/com/example/ecomerce/controller/SettingsController.java` | API cấu hình cửa hàng (`GET/PUT /settings`) | Luôn đọc/ghi bản ghi `id = 1` |
| `src/main/java/com/example/ecomerce/models/StoreSetting.java` | Entity bảng `settings` | Đúng 4 field theo scope đồ án |
| `src/main/java/com/example/ecomerce/repository/StoreSettingRepository.java` | Repository settings | Chủ yếu dùng `findById(1)` + `save` |
| `src/main/java/com/example/ecomerce/controller/InventoryController.java` | API kho: tồn kho, nhập/xuất/điều chỉnh, báo cáo | Điểm vào chính của module warehouse |
| `src/main/java/com/example/ecomerce/service/InventoryService.java` | Nghiệp vụ kho và ledger tồn | Nơi xử lý cập nhật tồn thực tế |
| `src/main/java/com/example/ecomerce/models/InventoryTransaction.java` | Entity giao dịch kho | Lưu lịch sử nhập/xuất/điều chỉnh |
| `src/main/java/com/example/ecomerce/models/InventoryTransactionType.java` | Enum loại giao dịch kho | Giúp phân tách báo cáo rõ ràng |
| `src/main/java/com/example/ecomerce/repository/InventoryTransactionRepository.java` | Repository ledger kho | Dùng cho report/tra cứu lịch sử |
| `src/main/java/com/example/ecomerce/controller/StatsController.java` | API thống kê nhanh sản phẩm (`/stats/products`) | Cần quyền `STATS_VIEW` |
| `src/main/java/com/example/ecomerce/controller/TestApiController.java` | API test Mouser và sync vào DB (`/api/admin/export-components-mouser`) | Chỉ `SUPER_ADMIN` cho API sync |
| `src/main/java/com/example/ecomerce/controller/NexarExportController.java` | API export SQL từ Nexar (`/api/admin/export-components`) | Có endpoint lấy category options cho UI |
| `src/main/java/com/example/ecomerce/service/MouserSyncService.java` | Logic gọi Mouser API và upsert product | Dùng cho trang Admin Data Sync |
| `src/main/java/com/example/ecomerce/service/NexarOAuthService.java` | Lấy/refresh token OAuth cho Nexar | Chỉ liên quan luồng Nexar |
| `src/main/java/com/example/ecomerce/service/NexarSqlExportService.java` | Generate SQL từ dữ liệu Nexar | Dùng khi cần import batch qua file SQL |

### 11.4 Frontend - điều hướng, quyền, auth, API client

| File | Chức năng chính | Ghi chú khi bảo trì |
|---|---|---|
| `admin-ui/src/App.jsx` | Router chính, map route -> page + chặn theo quyền | Nếu thêm trang mới, khai báo route tại đây |
| `admin-ui/src/layout/DashboardLayout.jsx` | Khung layout admin: sidebar, nhóm menu, topbar, thông báo tồn kho | Quyết định menu nào hiển thị theo role/permission |
| `admin-ui/src/components/ProtectedRoute.jsx` | Guard route theo login + role + permission | Route private đều đi qua component này |
| `admin-ui/src/api/http.js` | Axios instance + tự gắn Bearer token + xử lý 401 | 401 sẽ clear session và về `/login` |
| `admin-ui/src/auth/auth.js` | Lưu/đọc token, username, roles, permissions trong localStorage | Nơi chuẩn để thao tác auth state client |
| `admin-ui/src/main.jsx` | Entry point React app | Khởi tạo render App |

### 11.5 Frontend - các trang nghiệp vụ đang dùng

| File | Chức năng chính | Ghi chú khi bảo trì |
|---|---|---|
| `admin-ui/src/pages/Orders.jsx` | UI đơn hàng: tạo đơn, áp voucher, hủy đơn, hiển thị tiền `$` | Khi hủy đơn sẽ kéo theo release voucher + refund (backend) |
| `admin-ui/src/pages/Transactions.jsx` | UI giao dịch tài chính: bộ lọc, summary, tạo tay, confirm, reverse | Label tiếng Việt, tiền hiển thị USD |
| `admin-ui/src/pages/Vouchers.jsx` | UI quản lý voucher + chọn sản phẩm áp dụng + usage logs | Đã thay cách chọn sản phẩm để tránh crash form |
| `admin-ui/src/pages/Settings.jsx` | Form cấu hình đúng 4 input (Tên cửa hàng, SĐT, Địa chỉ, Mouser API key) | Save là ghi đè bản ghi settings id=1 |
| `admin-ui/src/pages/AdminDataSync.jsx` | UI đồng bộ linh kiện từ Mouser vào DB | Chỉ super admin truy cập |
| `admin-ui/src/pages/Inventory.jsx` | UI quản lý kho và nghiệp vụ nhập/xuất | Dựa trên API `/inventory/**` |
| `admin-ui/src/pages/Products.jsx` | UI danh sách/chỉnh sửa sản phẩm | Liên kết chặt với module tồn kho |
| `admin-ui/src/pages/Categories.jsx` | UI quản lý danh mục và thuộc tính theo danh mục | Dùng cho chuẩn hóa dữ liệu linh kiện |
| `admin-ui/src/pages/Dashboard.jsx` | Trang tổng quan | Điểm vào sau login |
| `admin-ui/src/pages/Login.jsx` | Form đăng nhập | Gọi `/auth/login` rồi lưu token/session |
| `admin-ui/src/pages/Profile.jsx` | Trang thông tin cá nhân | Dựa vào dữ liệu `/me` |
| `admin-ui/src/pages/ChangePassword.jsx` | Trang đổi mật khẩu (client flow) | Liên quan auth UX |
| `admin-ui/src/pages/Staffs.jsx` | Quản lý nhân sự/tài khoản | Cần quyền `USER_MANAGE` |
| `admin-ui/src/pages/RolesPermissions.jsx` | Màn quyền vai trò | Cần quyền `USER_MANAGE` |
| `admin-ui/src/pages/Customers.jsx` | Màn khách hàng (scope admin) | Cần quyền xem đơn toàn hệ thống |
| `admin-ui/src/pages/Reviews.jsx` | Màn đánh giá/review | Cùng scope vận hành CSKH |

### 11.6 File legacy/placeholder (đang không tham gia luồng chính)

| File | Trạng thái | Ghi chú |
|---|---|---|
| `admin-ui/src/pages/Blog.jsx` | Legacy/placeholder | Không được route trong `App.jsx` hiện tại |
| `admin-ui/src/pages/Banners.jsx` | Legacy/placeholder | Không được route trong `App.jsx` hiện tại |
| `src/main/java/com/example/ecomerce/service/BlogService.java` | Legacy/placeholder | Không có luồng controller/repository active tương ứng |
| `src/main/java/com/example/ecomerce/models/Blog.java` | Legacy/placeholder | Có thể dọn sau nếu chốt bỏ blog hẳn |
| `src/main/java/com/example/ecomerce/models/BlogCTA.java` | Legacy/placeholder | Thuộc cụm blog cũ |
| `src/main/java/com/example/ecomerce/models/BlogStatus.java` | Legacy/placeholder | Thuộc cụm blog cũ |

> Mẹo quay lại code nhanh: nếu cần lần theo logic một chức năng, luôn đi theo chuỗi:
> `Page (React) -> Controller (API) -> Service (business) -> Repository/Model (data)`.

