package com.phs.application.controller.shop;

import com.phs.application.entity.Brand;
import com.phs.application.entity.Category;
import com.phs.application.model.dto.PageableDTO;
import com.phs.application.model.dto.ProductInfoDTO;
import com.phs.application.security.ChatbotRateLimiter;
import com.phs.application.service.BrandService;
import com.phs.application.service.CategoryService;
import com.phs.application.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.text.NumberFormat;
import java.util.*;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    @Value("${groq.api-key:}")
    private String apiKey;

    @Value("${groq.api-url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    @Value("${groq.max-tokens:600}")
    private int maxTokens;

    @Value("${groq.temperature:0.5}")
    private double temperature;

    @Autowired
    private BrandService brandService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ChatbotRateLimiter rateLimiter;

    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body,
                                                    HttpServletRequest httpRequest) {
        Map<String, Object> resp = new HashMap<>();

        // Rate limit: tranh abuse + dot quota Groq
        if (!rateLimiter.tryAcquire(httpRequest)) {
            resp.put("reply", "Bạn đang gửi quá nhiều câu hỏi. Vui lòng chờ 1 phút rồi thử lại nhé!");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(resp);
        }

        try {
            String userMessage = body.get("message") != null ? body.get("message").toString().trim() : "";
            if (userMessage.isEmpty()) {
                resp.put("reply", "Bạn vui lòng nhập câu hỏi nhé!");
                return ResponseEntity.ok(resp);
            }

            if (apiKey == null || apiKey.trim().isEmpty()) {
                resp.put("reply", "Chatbot chưa được cấu hình. Vui lòng liên hệ quản trị viên để cài đặt Groq API key trong application properties.");
                return ResponseEntity.ok(resp);
            }

            // Parse history
            List<Map<String, String>> history = new ArrayList<>();
            Object rawHistory = body.get("history");
            if (rawHistory instanceof List) {
                for (Object item : (List<?>) rawHistory) {
                    if (item instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) item;
                        String role = String.valueOf(m.get("role"));
                        String content = m.get("content") != null ? m.get("content").toString() : "";
                        if (("user".equals(role) || "assistant".equals(role)) && !content.isEmpty()) {
                            Map<String, String> entry = new HashMap<>();
                            entry.put("role", role);
                            entry.put("content", content);
                            history.add(entry);
                        }
                    }
                }
            }
            if (history.size() > 10) {
                history = history.subList(history.size() - 10, history.size());
            }

            // Build messages
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> system = new HashMap<>();
            system.put("role", "system");
            system.put("content", buildSystemPrompt(userMessage, history));
            messages.add(system);
            messages.addAll(history);
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("model", model);
            reqBody.put("messages", messages);
            reqBody.put("max_tokens", maxTokens);
            reqBody.put("temperature", temperature);
            reqBody.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(reqBody, headers);
            RestTemplate rest = new RestTemplate();
            ResponseEntity<Map> groqResp = rest.exchange(apiUrl, HttpMethod.POST, entity, Map.class);

            String reply = extractReply(groqResp.getBody());
            resp.put("reply", reply != null && !reply.trim().isEmpty()
                    ? reply
                    : "Xin lỗi, mình chưa hiểu rõ câu hỏi. Bạn có thể hỏi lại được không?");
            return ResponseEntity.ok(resp);

        } catch (RestClientException ex) {
            resp.put("reply", "Hiện chatbot đang gặp sự cố kết nối. Bạn vui lòng thử lại sau ít phút hoặc gọi hotline 036xxxxxxx để được hỗ trợ trực tiếp nhé!");
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            resp.put("reply", "Đã có lỗi xảy ra. Bạn vui lòng thử lại sau hoặc gọi hotline 036xxxxxxx.");
            return ResponseEntity.ok(resp);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractReply(Map body) {
        if (body == null) return null;
        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List)) return null;
        List<?> choices = (List<?>) choicesObj;
        if (choices.isEmpty()) return null;
        Object first = choices.get(0);
        if (!(first instanceof Map)) return null;
        Object message = ((Map<?, ?>) first).get("message");
        if (!(message instanceof Map)) return null;
        Object content = ((Map<?, ?>) message).get("content");
        return content != null ? content.toString() : null;
    }

    /* ========== System prompt with live DB context (smart) ========== */

    private String buildSystemPrompt(String userMessage, List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();
        String contextText = (userMessage + " " + recentUserText(history)).toLowerCase();
        Intent intent = detectIntent(contextText);

        // ===== Persona + cac quy tac coi mo nhung chat che =====
        sb.append("Bạn là **Sneaker Bot** — chuyên gia tư vấn giày Shoes (Giày Phong Cách).\n");
        sb.append("Tính cách: thân thiện, hiểu biết về sneaker, dùng emoji vừa phải (👟✨🔥).\n\n");

        sb.append("QUY TẮC TỐI QUAN TRỌNG:\n");
        sb.append("1. CHỈ gợi ý sản phẩm trong danh sách \"SẢN PHẨM HIỆN CÓ\" dưới đây — KHÔNG bịa tên/giá/mã.\n");
        sb.append("2. Khi gợi ý: dùng định dạng `**Tên giày** — giá X đ — [/slug/id]` (link để khách click trực tiếp).\n");
        sb.append("3. Trả lời cô đọng: 3-6 câu cho câu hỏi đơn giản, tối đa 10 câu cho câu hỏi cần liệt kê.\n");
        sb.append("4. Khi đề xuất nhiều mẫu (≥2), DÙNG DANH SÁCH ĐÁNH SỐ.\n");
        sb.append("5. Hết phần liệt kê → gợi ý 1 hành động kế tiếp (\"Bạn xem chi tiết tại /san-pham nhé\" hoặc \"Liên hệ hotline nếu cần tư vấn thêm\").\n");
        sb.append("6. Nếu khách hỏi điều ngoài giày (chính trị, code AI, …): từ chối nhẹ và đề xuất chủ đề về giày.\n");
        sb.append("7. KHÔNG bịa mã giảm giá, ngày giao hàng cụ thể, hay tồn kho. Hướng dẫn khách kiểm tra trực tiếp.\n\n");

        // ===== Thong tin shop =====
        sb.append("THÔNG TIN SHOP:\n");
        sb.append("- Tên: Shoes (Giày Phong Cách)\n");
        sb.append("- Hotline: 036 545 4643 | Zalo: 0365454643\n");
        sb.append("- Địa chỉ: Hà Nội\n");
        sb.append("- Giờ mở: T2-CN 9:00-20:00\n");
        sb.append("- Size VN: 35-42\n\n");

        // ===== Brands + Categories =====
        List<Brand> brands = safeBrands();
        List<Category> categories = safeCategories();
        if (!brands.isEmpty()) {
            sb.append("THƯƠNG HIỆU: ");
            for (int i = 0; i < brands.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(brands.get(i).getName());
            }
            sb.append("\n");
        }
        if (!categories.isEmpty()) {
            sb.append("DANH MỤC: ");
            for (int i = 0; i < categories.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(categories.get(i).getName());
            }
            sb.append("\n");
        }
        sb.append("\n");

        // ===== Tinh hieu intent + bo sung context =====
        if (intent.hasAny()) {
            sb.append("BOT NHẬN ĐỊNH Ý ĐỊNH KHÁCH:\n");
            if (intent.wantsBestSeller) sb.append("- Khách muốn xem mẫu HOT/bán chạy.\n");
            if (intent.wantsNew) sb.append("- Khách quan tâm mẫu MỚI VỀ.\n");
            if (intent.wantsCheap) sb.append("- Khách hỏi giá RẺ/tầm thấp → ưu tiên giới thiệu giày giá thấp.\n");
            if (intent.askingSize) sb.append("- Khách đang hỏi VỀ SIZE → hướng dẫn cách chọn size (đo chân lúc chiều tối, để 1 ngón cái dư).\n");
            if (intent.askingCare) sb.append("- Khách hỏi BẢO QUẢN/VỆ SINH → tư vấn cụ thể (xịt waterproof, không giặt máy, dùng bàn chải mềm).\n");
            if (intent.askingShipping) sb.append("- Khách hỏi VẬN CHUYỂN → trả lời theo chính sách shop (miễn ship đơn ≥500k).\n");
            if (intent.askingReturn) sb.append("- Khách hỏi ĐỔI TRẢ → trả lời theo policy (7 ngày, sản phẩm còn mới 100%).\n");
            if (intent.askingPayment) sb.append("- Khách hỏi THANH TOÁN → COD, VNPay, chuyển khoản, Momo.\n");
            if (intent.askingOrder) sb.append("- Khách hỏi VỀ ĐƠN HÀNG → hướng dẫn vào /tai-khoan/lich-su-giao-dich.\n");
            if (intent.specificSize > 0) {
                sb.append("- Khách đề cập SIZE ").append(intent.specificSize).append(" → có thể nhắc về vừa size đó.\n");
            }
            if (intent.occasion != null) {
                sb.append("- Khách hỏi giày ").append(intent.occasion).append(" → gợi ý mẫu phù hợp dịp đó.\n");
            }
            if (intent.maxPrice > 0) {
                sb.append("- Khách giới hạn ngân sách ~").append(formatVnd(intent.maxPrice))
                  .append("đ → chỉ giới thiệu giày dưới mức này.\n");
            }
            sb.append("\n");
        }

        // ===== Brand matched in message =====
        Set<String> matchedBrands = new LinkedHashSet<>();
        Map<String, ProductInfoDTO> picked = new LinkedHashMap<>();
        for (Brand b : brands) {
            if (b.getName() == null) continue;
            String bn = b.getName().toLowerCase();
            if (contextText.contains(bn) || containsFuzzy(contextText, bn)) {
                matchedBrands.add(b.getName());
                addProducts(picked, safeSearch(b.getName()), 10);
            }
        }

        // ===== Pull intent-based lists =====
        if (intent.wantsBestSeller) addProducts(picked, safeBest(), 10);
        if (intent.wantsNew)        addProducts(picked, safeNew(), 10);
        if (intent.wantsViewed)     {
            try {
                addProducts(picked, productService.getListViewProducts(), 8);
            } catch (Exception ignore) {}
        }

        // ===== Fallback =====
        if (picked.isEmpty()) {
            addProducts(picked, safeNew(), 6);
            addProducts(picked, safeBest(), 6);
        }

        // ===== Filter by max price + Sort =====
        List<ProductInfoDTO> productList = new ArrayList<>(picked.values());
        if (intent.maxPrice > 0) {
            final long cap = intent.maxPrice;
            productList.removeIf(p -> effectivePrice(p) > cap);
            // Neu khong con san pham nao thoa nguong → bo loc va inject thong bao
            if (productList.isEmpty()) {
                productList = new ArrayList<>(picked.values());
                sb.append("LƯU Ý: Hiện không có giày nào dưới ").append(formatVnd(cap))
                  .append("đ — bot có thể giới thiệu mẫu giá tốt nhất kèm gợi ý chờ sale.\n\n");
            }
        }
        if (intent.wantsCheap) {
            productList.sort(Comparator.comparingLong(this::effectivePrice));
        } else if (intent.wantsBestSeller) {
            productList.sort((a, b) -> Integer.compare(b.getTotalSold(), a.getTotalSold()));
        }

        // Tang cap len 18 san pham (giup bot nhieu lua chon hon)
        if (productList.size() > 18) productList = productList.subList(0, 18);

        // ===== Section san pham =====
        sb.append("SẢN PHẨM HIỆN CÓ ");
        if (!matchedBrands.isEmpty()) {
            sb.append("(ưu tiên: ").append(String.join(", ", matchedBrands)).append("):\n");
        } else {
            sb.append("(tham khảo để gợi ý cho khách):\n");
        }
        if (productList.isEmpty()) {
            sb.append("(Hiện chưa lấy được danh sách sản phẩm — hãy đề nghị khách xem /san-pham)\n");
        } else {
            for (ProductInfoDTO p : productList) {
                long eff = effectivePrice(p);
                sb.append("- ").append(p.getName());
                sb.append(" | ").append(formatVnd(eff)).append("đ");
                if (p.getPromotionPrice() > 0 && p.getPromotionPrice() < p.getPrice()) {
                    sb.append(" (giảm từ ").append(formatVnd(p.getPrice())).append("đ)");
                }
                if (p.getTotalSold() > 0) sb.append(" | đã bán ").append(p.getTotalSold());
                sb.append(" | /").append(p.getSlug()).append("/").append(p.getId()).append("\n");
            }
        }
        sb.append("\n");

        // ===== Policy + Links =====
        sb.append("CHÍNH SÁCH:\n");
        sb.append("- Đổi trả: 7 ngày kể từ ngày nhận, sản phẩm còn mới 100%, nguyên hộp + tag.\n");
        sb.append("- Vận chuyển: miễn phí đơn từ 500.000đ. Miễn ship nếu lỗi từ shop.\n");
        sb.append("- Thanh toán: COD, VNPay, chuyển khoản ngân hàng, Momo.\n\n");

        sb.append("LIÊN KẾT:\n");
        sb.append("- /san-pham — danh sách + filter\n");
        sb.append("- /lien-he — liên hệ & bản đồ\n");
        sb.append("- /huong-dan — hướng dẫn mua hàng\n");
        sb.append("- /doi-hang — chính sách đổi trả\n");
        sb.append("- /tai-khoan/lich-su-giao-dich — đơn hàng của khách\n\n");

        // ===== Few-shot examples (giup bot tra loi nhat quan) =====
        sb.append("VÍ DỤ TRẢ LỜI MẪU:\n");
        sb.append("Q: \"Có mẫu Vans nào hot không?\"\n");
        sb.append("A: \"👟 Bên mình có vài mẫu Vans bán rất chạy:\\n1. **Vans Vault Style 36 Black** — 1.999.999đ — /vans-vault-style-36-black/abc\\n2. ...\\nBạn xem chi tiết và đặt tại /san-pham nhé!\"\n\n");
        sb.append("Q: \"Mình đi học, chọn giày nào ổn?\"\n");
        sb.append("A: \"Cho đi học hằng ngày, mình recommend mẫu thoải mái, dễ phối: 1. **Tên** — giá — link. 2. ... Bạn thường mang size mấy để mình tư vấn tiếp nhé?\"\n\n");

        return sb.toString();
    }

    /* ===== Intent detection ===== */

    private static class Intent {
        boolean wantsBestSeller;
        boolean wantsNew;
        boolean wantsCheap;
        boolean wantsViewed;
        boolean askingSize;
        boolean askingCare;
        boolean askingShipping;
        boolean askingReturn;
        boolean askingPayment;
        boolean askingOrder;
        int specificSize;       // 35-42, 0 = none
        long maxPrice;          // VND, 0 = none
        String occasion;        // "đi học", "đi chơi", "đá bóng", ...

        boolean hasAny() {
            return wantsBestSeller || wantsNew || wantsCheap || wantsViewed
                    || askingSize || askingCare || askingShipping || askingReturn
                    || askingPayment || askingOrder
                    || specificSize > 0 || maxPrice > 0 || occasion != null;
        }
    }

    private Intent detectIntent(String text) {
        Intent it = new Intent();
        if (text == null) return it;

        it.wantsBestSeller = containsAny(text,
                "bán chạy", "ban chay", "hot", "phổ biến", "nhiều người mua", "best seller", "bestseller");
        it.wantsNew = containsAny(text,
                "mới về", "moi ve", "mới nhất", "moi nhat", "vừa về", "new arrival", "mẫu mới");
        it.wantsCheap = containsAny(text,
                "rẻ", "re ", "giá thấp", "tiết kiệm", "budget", "giá mềm", "rẻ nhất", "tầm thấp");
        it.wantsViewed = containsAny(text,
                "xem nhiều", "xem nhieu", "trending", "đang trend", "trend");

        it.askingSize = containsAny(text,
                "size", "kích cỡ", "kich co", "vừa chân", "vua chan", "size mấy", "chọn size");
        it.askingCare = containsAny(text,
                "vệ sinh", "ve sinh", "bảo quản", "bao quan", "giặt giày", "giat giay", "tẩy", "lau giày", "chăm sóc giày");
        it.askingShipping = containsAny(text,
                "giao hàng", "ship ", "shipper", "vận chuyển", "van chuyen", "phí ship", "bao lâu giao");
        it.askingReturn = containsAny(text,
                "đổi trả", "doi tra", "hoàn tiền", "hoan tien", "trả hàng", "tra hang", "refund");
        it.askingPayment = containsAny(text,
                "thanh toán", "thanh toan", "trả tiền", "tra tien", "vnpay", "momo", "cod", "chuyển khoản");
        it.askingOrder = containsAny(text,
                "đơn của tôi", "đơn hàng của", "track đơn", "tra cứu đơn", "đến đâu rồi", "lịch sử mua");

        // Specific size (35-42)
        java.util.regex.Matcher sizeM = java.util.regex.Pattern
                .compile("(?<![0-9])(3[5-9]|4[0-2])(?![0-9])")
                .matcher(text);
        if (sizeM.find()) {
            try { it.specificSize = Integer.parseInt(sizeM.group(1)); } catch (NumberFormatException ignore) {}
        }

        // Max price: "dưới X", "tầm X", "khoảng X" với X dạng "1tr", "500k", "1 triệu", "1500000"
        it.maxPrice = parseMaxPrice(text);

        // Occasion
        if (containsAny(text, "đi học", "di hoc", "đến trường")) it.occasion = "đi học";
        else if (containsAny(text, "đi làm", "công sở", "văn phòng")) it.occasion = "đi làm";
        else if (containsAny(text, "đá bóng", "đá banh", "đa bong")) it.occasion = "đá bóng";
        else if (containsAny(text, "chạy bộ", "chay bo", "tập gym", "tap gym", "thể thao")) it.occasion = "tập luyện thể thao";
        else if (containsAny(text, "đi chơi", "di choi", "đi cà phê", "dạo phố", "casual")) it.occasion = "đi chơi/casual";
        else if (containsAny(text, "đi cưới", "đám cưới", "dự tiệc", "lịch sự", "formal")) it.occasion = "dự sự kiện trang trọng";

        return it;
    }

    private boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) return true;
        }
        return false;
    }

    private boolean containsFuzzy(String text, String needle) {
        // Match khi user go khong dau: "vans" trong "co mau van nao khong" → false
        // Don gian: bo dau giua needle de check
        // (Co the nang cap bang Normalizer sau)
        return text.contains(needle);
    }

    private long parseMaxPrice(String text) {
        // Tim cum "dưới/duoi/tầm/khoảng/tối đa X[k/tr/triệu]"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?:dưới|duoi|tầm|tam|khoảng|khoang|tối đa|toi da|under)\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(triệu|trieu|tr|k|nghìn|nghin|m|đ|vnd)?"
        ).matcher(text);
        if (!m.find()) return 0;
        try {
            double num = Double.parseDouble(m.group(1).replace(",", "."));
            String unit = m.group(2);
            if (unit == null) {
                // Khong don vi: doan dua tren do lon
                if (num < 100) return (long) (num * 1_000_000);   // "dưới 2" → 2tr
                if (num < 1000) return (long) (num * 1_000);       // "dưới 500" → 500k
                return (long) num;
            }
            switch (unit) {
                case "triệu": case "trieu": case "tr": case "m":
                    return (long) (num * 1_000_000);
                case "k": case "nghìn": case "nghin":
                    return (long) (num * 1_000);
                default:
                    return (long) num;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private String recentUserText(List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = history.size() - 1; i >= 0 && count < 3; i--) {
            Map<String, String> m = history.get(i);
            if ("user".equals(m.get("role"))) {
                sb.append(" ").append(m.get("content"));
                count++;
            }
        }
        return sb.toString();
    }

    private long effectivePrice(ProductInfoDTO p) {
        if (p.getPromotionPrice() > 0 && p.getPromotionPrice() < p.getPrice()) {
            return p.getPromotionPrice();
        }
        return p.getPrice();
    }

    private String formatVnd(long amount) {
        return VND.format(amount);
    }

    private void addProducts(Map<String, ProductInfoDTO> bag, List<ProductInfoDTO> items, int max) {
        if (items == null) return;
        int added = 0;
        for (ProductInfoDTO p : items) {
            if (p == null || p.getId() == null) continue;
            if (!bag.containsKey(p.getId())) {
                bag.put(p.getId(), p);
                added++;
                if (added >= max) break;
            }
        }
    }

    /* ===== Safe fetch helpers (swallow errors so chatbot still answers) ===== */

    private List<Brand> safeBrands() {
        try {
            List<Brand> list = brandService.getListBrand();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private List<Category> safeCategories() {
        try {
            List<Category> list = categoryService.getListCategories();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private List<ProductInfoDTO> safeBest() {
        try {
            List<ProductInfoDTO> list = productService.getListBestSellProducts();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private List<ProductInfoDTO> safeNew() {
        try {
            List<ProductInfoDTO> list = productService.getListNewProducts();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) { return Collections.emptyList(); }
    }

    @SuppressWarnings("unchecked")
    private List<ProductInfoDTO> safeSearch(String keyword) {
        try {
            PageableDTO page = productService.searchProductByKeyword(keyword, 1);
            if (page != null && page.getItems() instanceof List) {
                return (List<ProductInfoDTO>) page.getItems();
            }
            return Collections.emptyList();
        } catch (Exception e) { return Collections.emptyList(); }
    }
}
