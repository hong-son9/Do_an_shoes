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

> Nếu chưa cấu hình SMTP, đăng ký và quên mật khẩu sẽ **in OTP ra console** thay vì gửi mail. Xem [Mục 6](#6-email--otp-đăng-ký--quên-mật-khẩu) để bật gửi mail thật.

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

## 6. Email & OTP (đăng ký / quên mật khẩu)

Ứng dụng dùng **Gmail SMTP** để gửi mã OTP 6 số khi user **đăng ký tài khoản** hoặc **quên mật khẩu**. OTP có hiệu lực **1 phút**, mỗi email có cooldown **60 giây** giữa các lần gửi.

### 6.1. Chế độ dev (không cần SMTP)

Nếu chưa set `MAIL_USERNAME` / `MAIL_PASSWORD`, ứng dụng **không gửi email thật** mà **in OTP ra console**:

```
[DEV] OTP for register:user@gmail.com = 312487 (mail not configured)
[DEV] OTP for reset:user@gmail.com = 904571 (mail not configured)
```

→ Copy OTP từ console, dán vào form là dùng được. Đây là cách nhanh nhất để test luồng đăng ký/quên mật khẩu.

### 6.2. Bật gửi email thật qua Gmail

#### Bước 1 — Tạo App Password Gmail

Gmail không cho dùng mật khẩu thường để login SMTP. Bạn cần tạo **App Password**:

1. Bật **2-Step Verification** tại <https://myaccount.google.com/security> (bắt buộc, không có 2FA không tạo được App Password).
2. Vào <https://myaccount.google.com/apppasswords>.
3. Đặt tên app (vd: `Shoes Dev`) → **Tạo**.
4. Google hiển thị mã **16 ký tự** dạng `xxxx xxxx xxxx xxxx`. **Copy ngay** — Google chỉ hiện 1 lần.
5. Bỏ khoảng trắng để có chuỗi liền 16 ký tự.

#### Bước 2 — Set biến môi trường

> **KHÔNG** hardcode mật khẩu vào file `application-dev.properties`. Properties này có thể bị commit lên Git và lộ credential — giống vụ leak Groq API key trước đây.

**Windows (PowerShell)** — vĩnh viễn, dùng được cho mọi terminal/IDE sau này:

```powershell
[Environment]::SetEnvironmentVariable("MAIL_USERNAME", "your_email@gmail.com", "User")
[Environment]::SetEnvironmentVariable("MAIL_PASSWORD", "xxxxxxxxxxxxxxxx", "User")
```

Hoặc qua GUI: **Windows + R** → `sysdm.cpl` → tab **Advanced** → **Environment Variables** → User variables → **New** → `MAIL_USERNAME` / `MAIL_PASSWORD`.

**Linux/macOS** (bash/zsh) — thêm vào `~/.bashrc` hoặc `~/.zshrc`:

```bash
export MAIL_USERNAME="your_email@gmail.com"
export MAIL_PASSWORD="xxxxxxxxxxxxxxxx"
```

Sau đó `source ~/.bashrc` (hoặc mở terminal mới).

#### Bước 3 — Restart Spring Boot

Đóng terminal đang chạy app, mở terminal **mới** (để load env var mới):

```powershell
echo $env:MAIL_USERNAME      # verify đã set
mvn spring-boot:run
```

#### Bước 4 — Test

1. Vào <http://localhost:8081> → click 👤 trên header.
2. **Đăng ký**: điền form → click **Gửi OTP** → check inbox email vừa nhập → nhập OTP + mật khẩu → đăng ký xong là tự đăng nhập.
3. **Quên mật khẩu**: từ modal đăng nhập click **"Quên mật khẩu?"** → nhập email đã đăng ký → Gửi OTP → check inbox → nhập OTP + mật khẩu mới → đặt lại.

### 6.3. Cấu hình bổ sung

Trong [src/main/resources/application-dev.properties](src/main/resources/application-dev.properties):

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true

# OTP config
otp.expiry-minutes=5            # OTP hết hạn sau X phút
otp.resend-cooldown-seconds=60  # Bắt user chờ X giây trước khi gửi lại
```

### 6.4. Khắc phục sự cố mail

| Lỗi trong Spring console | Nguyên nhân | Cách fix |
|--------------------------|-------------|----------|
| `Authentication failed` | App Password sai hoặc đã bị Google revoke | Tạo App Password mới tại <https://myaccount.google.com/apppasswords> |
| `Could not connect to SMTP host` | Mạng chặn port 587 (hiếm) | Đổi mạng, tắt VPN, hoặc dùng port 465 với `mail.smtp.ssl.enable=true` |
| `Username and Password not accepted` | Chưa bật 2FA hoặc dùng password thường | Bật 2FA + dùng App Password 16 ký tự, KHÔNG dùng password Gmail thường |
| App in `[DEV] OTP` thay vì gửi mail | `MAIL_USERNAME` / `MAIL_PASSWORD` chưa set hoặc terminal cũ | Mở terminal **mới** sau khi set env var, verify `echo $env:MAIL_USERNAME` |
| Email không tới inbox | Có thể trong **Spam folder** | Check Spam; Gmail tới chậm 5-30s sau khi gửi |

### 6.5. Bảo mật

- **OTP một lần dùng**: xoá khỏi cache ngay sau khi verify thành công.
- **Cooldown 60s/email/purpose**: chống spam OTP.
- **Anti-enumeration** (quên mật khẩu): nếu email không tồn tại trong DB, vẫn trả "success" để attacker không dò ra được danh sách email đã đăng ký.
- **App Password**: chỉ cho phép gửi mail; **KHÔNG có quyền** đọc inbox hay đổi mật khẩu Gmail. Nếu lộ, vào <https://myaccount.google.com/apppasswords> revoke ngay.
- **`.gitignore`**: nên thêm pattern để chặn commit nhầm credential. Hoặc kiểm tra `git diff` trước khi commit.

### 6.6. Các endpoint OTP

| Method | URL                              | Mô tả                            |
|--------|----------------------------------|----------------------------------|
| POST   | `/api/register/send-otp`         | Gửi OTP cho đăng ký (body: `{email}`) |
| POST   | `/api/register`                  | Hoàn tất đăng ký (body có `otp`) |
| POST   | `/api/forgot-password/send-otp`  | Gửi OTP đặt lại mật khẩu         |
| POST   | `/api/forgot-password/reset`     | Đặt lại mật khẩu mới             |


## 7. Chatbot AI (Groq)

Cửa hàng tích hợp **Sneaker Bot** — chatbot tư vấn giày dùng LLM (mặc định `llama-3.3-70b-versatile`) qua [Groq Cloud](https://groq.com). Bot biết cấu trúc DB: list brand, category, sản phẩm đang bán, khuyến mãi → giúp khách chọn giày, hỏi size, tư vấn theo dịp/ngân sách. Nếu chưa cấu hình, chatbot vẫn hiện nhưng trả lời "chưa được cấu hình".

### 7.1. Lấy API key

1. Đăng ký tài khoản tại <https://console.groq.com> (Groq cho **free tier** thoải mái).
2. Vào <https://console.groq.com/keys> → **Create API Key** → đặt tên (vd `shoes-shop`).
3. Copy key dạng `gsk_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` — **chỉ hiện 1 lần**.

### 7.2. Set biến môi trường

> **KHÔNG** paste API key vào `application-dev.properties` → tránh lộ khi commit Git (GitHub có push protection sẽ tự chặn).

**Windows (PowerShell):**

```powershell
[Environment]::SetEnvironmentVariable("GROQ_API_KEY", "gsk_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "User")
```

**Linux/macOS:**

```bash
export GROQ_API_KEY="gsk_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
# Thêm vào ~/.bashrc hoặc ~/.zshrc để persist
```

### 7.3. Tuỳ chỉnh model / token

Trong [application-dev.properties](src/main/resources/application-dev.properties):

```properties
groq.api-key=${GROQ_API_KEY:}
groq.api-url=https://api.groq.com/openai/v1/chat/completions
groq.model=llama-3.3-70b-versatile  # hoặc llama-3.1-8b-instant (nhanh hơn, kém thông minh hơn)
groq.max-tokens=600
groq.temperature=0.5                # 0=máy móc, 1=sáng tạo
```

### 7.4. Test

1. Restart Spring Boot (mở terminal mới sau khi set env var).
2. Vào <http://localhost:8081> → icon chat 💬 góc dưới phải → mở Sneaker Bot.
3. Thử các câu hỏi:
   - "Có Nike size 40 nào hot không?"
   - "Mình đi học, recommend giày dưới 2 triệu"
   - "Cách giặt giày Vans?"

Chatbot có **rate limit 20 req/phút/IP** để tránh đốt quota Groq.

---

## 8. Đăng nhập bằng Google (OAuth 2.0)

Cho phép user đăng nhập bằng tài khoản Google chỉ với 1 click. Nếu email Google chưa có trong DB, hệ thống tự tạo user mới (role `USER`, password ngẫu nhiên).

### 8.1. Tạo OAuth Client trên Google Cloud Console

1. Vào <https://console.cloud.google.com/apis/credentials>.
2. Tạo project mới nếu chưa có (vd `Sơn Shoes`).
3. Nếu chưa cấu hình **OAuth consent screen**:
   - **User Type**: External
   - App name: `Shoes`
   - User support email + Developer contact email: email của bạn
   - Scopes: không cần thêm (mặc định đã đủ `email` + `profile`)
   - **Test users**: thêm các Gmail dùng để test trong giai đoạn dev
4. Quay lại Credentials → **+ CREATE CREDENTIALS** → **OAuth client ID**:
   - **Application type**: Web application
   - **Name**: `Shoes Local Dev`
   - **Authorized JavaScript origins**: `http://localhost:8081`
   - **Authorized redirect URIs**: `http://localhost:8081/login/oauth2/code/google`
5. Click **Create** → popup hiện **Client ID** + **Client Secret** → copy cả 2.

### 8.2. Set biến môi trường

```powershell
# Windows
[Environment]::SetEnvironmentVariable("GOOGLE_CLIENT_ID", "1234567890-xxxxxxxxxx.apps.googleusercontent.com", "User")
[Environment]::SetEnvironmentVariable("GOOGLE_CLIENT_SECRET", "GOCSPX-xxxxxxxxxxxxxxxxxx", "User")
```

```bash
# Linux/macOS
export GOOGLE_CLIENT_ID="1234567890-xxxxxxxxxx.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="GOCSPX-xxxxxxxxxxxxxxxxxx"
```

### 8.3. Cấu hình tự động

Phần config trong properties đã chuẩn:

```properties
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID:placeholder-client-id}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET:placeholder-secret}
spring.security.oauth2.client.registration.google.scope=email,profile
spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
```

> Khi chưa set env var, app vẫn chạy (dùng `placeholder-*`) nhưng click nút Google sẽ lỗi `401 invalid_client` từ Google. Set env var → restart → hoạt động.

### 8.4. Luồng hoạt động

1. User click **"Đăng nhập bằng Google"** trên modal đăng nhập.
2. Browser redirect đến Google OAuth.
3. User chọn tài khoản + đồng ý cho phép truy cập email + profile.
4. Google redirect về `http://localhost:8081/login/oauth2/code/google?code=xxx`.
5. Spring Security đổi `code` lấy token Google → gọi userinfo lấy email + tên.
6. [OAuth2LoginSuccessHandler](src/main/java/com/phs/application/security/OAuth2LoginSuccessHandler.java):
   - Tìm user theo email; nếu chưa có thì **tạo mới** (password random, role USER).
   - Sinh JWT, set cookie `JWT_TOKEN` (HttpOnly + SameSite=Lax).
   - Redirect về `/?oauth=success`.
7. Trang chủ load lại với header hiện tên user → đã đăng nhập ✅.

### 8.5. Khắc phục sự cố

| Lỗi | Nguyên nhân | Fix |
|---|---|---|
| `redirect_uri_mismatch` từ Google | Authorized redirect URI sai | Phải khớp **chính xác** `http://localhost:8081/login/oauth2/code/google` |
| 404 ở `/oauth2/authorization/google` | Chưa restart sau khi thêm dependency | `mvn spring-boot:run` lại |
| Redirect về `/?oauthError=auth_failed` | Client ID/Secret sai hoặc env var chưa load | Verify `echo $env:GOOGLE_CLIENT_ID`, mở terminal mới |
| `Access blocked: This app's request is invalid` | App ở chế độ Testing, email chưa thêm vào Test users | Add Test user trong OAuth consent screen hoặc Publish app |
| User Google đã có account password sẵn | Service lookup theo email → trả user cũ | Tài khoản đó dùng được cả 2 cách login |

---

## 9. Cấu trúc dự án

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

## 10. Một số endpoint quan trọng

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

## 11. Khắc phục sự cố

- **`Sai chữ ký` (VNPay code 70)**: Kiểm tra lại `vnpay.tmn-code` và `vnpay.hash-secret` trong properties. Đảm bảo copy chính xác, không có ký tự space ở đầu/cuối, và đúng cặp được VNPay gửi qua email.
- **`localhost` bị từ chối khi đăng ký merchant**: VNPay không cho URL localhost. Khi đăng ký dùng domain giả như `http://shoes-demo.com`. Còn `vnp_ReturnUrl` trong code vẫn để `http://localhost:8081/...` được.
- **Không hiện badge giỏ hàng**: Đảm bảo đã đăng nhập. Endpoint `/api/cart/count` trả 0 cho khách chưa login.
- **Bảng `cart_item` không tồn tại**: Chạy lại app — Hibernate sẽ tự `CREATE TABLE` nhờ `ddl-auto=update`.
- **Lỗi `Could not locate appropriate constructor`**: Khi sửa entity/DTO liên quan đến `@SqlResultSetMapping`, cần đảm bảo constructor khớp số lượng và kiểu tham số. Sau khi sửa nên restart full app (không chỉ devtools reload).
