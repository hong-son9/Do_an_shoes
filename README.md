# Shoes Shop — Hướng dẫn sử dụng

Ứng dụng bán giày trực tuyến viết bằng Spring Boot + Thymeleaf + MySQL, có tích hợp giỏ hàng và thanh toán VNPay (sandbox).

## 1. Yêu cầu môi trường

- **Java**: JDK 11
- **MySQL**: 5.7+ hoặc 8.0
- **Maven**: 3.6+ (đã bundled `mvnw` trong repo)
- **Port mặc định**: `8081`

## 2. Cài đặt và chạy

### 2.1. Cấu hình database

Mặc định ứng dụng kết nối tới MySQL local theo cấu hình tại [src/main/resources/application-dev.properties](src/main/resources/application-dev.properties):

```
spring.datasource.url=jdbc:mysql://localhost:3306/shoes?...
spring.datasource.username=root
spring.datasource.password=12345
```

Sửa lại `username` / `password` cho khớp với MySQL của bạn. Database `shoes` sẽ tự động tạo nhờ tham số `createDatabaseIfNotExist=true`. Hibernate cấu hình `ddl-auto=update` nên các bảng cũng tự tạo khi khởi động lần đầu.

### 2.2. Khởi động ứng dụng

Trên Windows (PowerShell):

```
.\mvnw.cmd spring-boot:run
```

Trên Linux/macOS:

```
./mvnw spring-boot:run
```

Mở trình duyệt: <http://localhost:8081>

## 3. Tài khoản mẫu

| Vai trò | Email              | Mật khẩu   |
|---------|--------------------|------------|
| Admin   | admin@gmail.com    | admin@123  |
| User    | user@gmail.com     | user@123   |

- Admin sau khi đăng nhập truy cập: <http://localhost:8081/admin>
- User dùng để mua hàng và test luồng giỏ hàng + thanh toán.

> Nếu các tài khoản này chưa tồn tại trong DB của bạn, hãy đăng ký mới ở góc phải header, hoặc insert trực tiếp vào bảng `users` (password lưu dạng BCrypt).

## 4. Các tính năng chính

### 4.1. Phía người dùng

- **Trang chủ**: danh sách sản phẩm mới, bán chạy, được xem nhiều.
- **Tìm kiếm / lọc sản phẩm**: theo thương hiệu, danh mục, khoảng giá, size.
- **Chi tiết sản phẩm**:
  - Chọn size giày.
  - Nhấn **Mua ngay** → chuyển sang trang đặt hàng nhanh cho 1 sản phẩm.
  - Nhấn **Thêm vào giỏ hàng** → lưu vào giỏ.
- **Giỏ hàng** (icon biểu tượng giỏ trên header, đường dẫn `/gio-hang`):
  - Cập nhật số lượng, xóa từng sản phẩm.
  - Nhấn **Đặt hàng** → modal nhập thông tin nhận hàng + chọn phương thức thanh toán:
    - **Thanh toán trực tiếp (COD)**: tạo đơn ngay, giỏ hàng được xóa.
    - **Thanh toán qua VNPay**: tạo đơn nhưng **giỏ hàng được giữ lại** cho tới khi thanh toán thành công.
- **Lịch sử giao dịch**: `/tai-khoan/lich-su-giao-dich` — quản lý đơn hàng theo trạng thái.

### 4.2. Phía admin (`/admin`)

- Dashboard thống kê doanh thu.
- Quản lý: sản phẩm, danh mục, thương hiệu, khuyến mại, đơn hàng, bài viết, người dùng.

## 5. Thanh toán VNPay (sandbox)

### 5.1. Cấu hình

File [src/main/resources/application-dev.properties](src/main/resources/application-dev.properties):

```properties
vnpay.tmn-code=UFARMUW0
vnpay.hash-secret=IWFUMFVVEE5HNUPB6GDXX6IRLZAAY639
vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
vnpay.return-url=http://localhost:8081/payment/vnpay/return
```

> `tmn-code` và `hash-secret` là cấp từ VNPay khi đăng ký merchant sandbox tại <https://sandbox.vnpayment.vn/devreg/>. Nếu bạn dùng tài khoản khác, hãy thay 2 giá trị này.

### 5.2. Thông tin thẻ test VNPay sandbox

Sau khi click thanh toán → trang VNPay → chọn **Ngân hàng NCB** → nhập thông tin sau:

| Trường            | Giá trị                  |
|-------------------|--------------------------|
| Ngân hàng         | NCB                      |
| Số thẻ            | `9704198526191432198`    |
| Tên chủ thẻ       | `NGUYEN VAN A`           |
| Ngày phát hành    | `07/15`                  |
| Mã OTP            | `123456`                 |

Một số thẻ test khác (dùng cho các case lỗi):

| Trường hợp                | Số thẻ                  |
|---------------------------|-------------------------|
| Thẻ không đủ số dư        | `9704195798459170488`   |
| Thẻ chưa kích hoạt        | `9704192181368742`      |
| Visa (no 3DS)             | `4456530000001005`      |
| Mastercard (no 3DS)       | `5200000000001005`      |

### 5.3. Luồng thanh toán

1. Người dùng đăng nhập → thêm sản phẩm vào giỏ → vào `/gio-hang`.
2. Nhấn **Đặt hàng** → chọn **Thanh toán qua VNPay** → xác nhận.
3. Ứng dụng tạo đơn (status = chờ lấy hàng), build URL ký HMAC-SHA512 rồi redirect sang sandbox VNPay.
4. Nhập thông tin thẻ test ở mục 5.2 → bấm Tiếp tục → nhập OTP `123456`.
5. VNPay redirect về `http://localhost:8081/payment/vnpay/return`:
   - Hệ thống verify chữ ký, nếu thành công thì **clear giỏ hàng** + hiển thị trang kết quả.
   - Nếu hủy/lỗi: giỏ hàng vẫn còn nguyên, user có thể thử lại.

## 6. Cấu trúc dự án

```
src/main/java/com/phs/application/
├── Application.java           # Entry point
├── config/                    # Hằng số
├── controller/
│   ├── admin/                 # Endpoint cho /admin/**
│   └── shop/                  # Endpoint phía user (HomeController, CartController, VNPayController...)
├── entity/                    # JPA entities (Product, Order, CartItem, User...)
├── exception/                 # BadRequest/NotFound + handler
├── model/
│   ├── dto/                   # DTO trả ra view/JSON
│   ├── mapper/                # MapStruct-style mappers
│   └── request/               # Request body classes
├── repository/                # Spring Data JPA repositories
├── security/                  # JWT filter, UserDetails, WebSecurityConfig
└── service/                   # Business logic + impl/

src/main/resources/
├── application-dev.properties # Cấu hình dev (DB, VNPay)
├── static/shop/               # CSS/JS/images phía user
├── static/adminlte/           # AdminLTE template cho admin
└── templates/
    ├── admin/                 # View admin
    ├── fragment/              # Header/footer/modal dùng chung
    ├── layout/                # Layout chính
    └── shop/                  # View phía user
```

## 7. Một số endpoint quan trọng

| Method | URL                              | Mô tả                                    |
|--------|----------------------------------|------------------------------------------|
| GET    | `/`                              | Trang chủ                                |
| GET    | `/san-pham`                      | Danh sách sản phẩm                       |
| GET    | `/{slug}/{id}`                   | Chi tiết sản phẩm                        |
| GET    | `/gio-hang`                      | Trang giỏ hàng                           |
| POST   | `/api/cart/items`                | Thêm vào giỏ                             |
| PUT    | `/api/cart/items/{id}`           | Cập nhật số lượng                        |
| DELETE | `/api/cart/items/{id}`           | Xóa khỏi giỏ                             |
| POST   | `/api/cart/checkout`             | Đặt hàng từ giỏ                          |
| GET    | `/api/cart/count`                | Số lượng item trong giỏ (cho badge)      |
| POST   | `/api/orders`                    | Đặt hàng nhanh (Mua ngay)                |
| POST   | `/api/payment/vnpay/create`      | Tạo URL thanh toán VNPay                 |
| GET    | `/payment/vnpay/return`          | VNPay callback                           |
| GET    | `/tai-khoan/lich-su-giao-dich`   | Lịch sử giao dịch                        |
| GET    | `/admin/**`                      | Trang admin (yêu cầu role ADMIN)         |

## 8. Khắc phục sự cố

- **`Sai chữ ký` (VNPay code 70)**: Kiểm tra lại `vnpay.tmn-code` và `vnpay.hash-secret` trong properties. Đảm bảo copy chính xác, không có ký tự space ở đầu/cuối, và đúng cặp được VNPay gửi qua email.
- **`localhost` bị từ chối khi đăng ký merchant**: VNPay không cho URL localhost. Khi đăng ký dùng domain giả như `http://shoes-demo.com`. Còn `vnp_ReturnUrl` trong code vẫn để `http://localhost:8081/...` được.
- **Không hiện badge giỏ hàng**: Đảm bảo đã đăng nhập. Endpoint `/api/cart/count` trả 0 cho khách chưa login.
- **Bảng `cart_item` không tồn tại**: Chạy lại app — Hibernate sẽ tự `CREATE TABLE` nhờ `ddl-auto=update`.
- **Lỗi `Could not locate appropriate constructor`**: Khi sửa entity/DTO liên quan đến `@SqlResultSetMapping`, cần đảm bảo constructor khớp số lượng và kiểu tham số. Sau khi sửa nên restart full app (không chỉ devtools reload).
