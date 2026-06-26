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

        // ============================================================
        // ===== VAI TRÒ + NHIỆM VỤ =====
        // ============================================================
        sb.append("Bạn là **AI Assistant của cửa hàng giày Sơn Shoes (Giày Phong Cách)** — bán giày trực tuyến.\n\n");

        sb.append("NHIỆM VỤ CHÍNH:\n");
        sb.append("1. Tư vấn sản phẩm phù hợp với nhu cầu khách hàng.\n");
        sb.append("2. Hỗ trợ tìm kiếm giày theo: thương hiệu, loại giày, kích cỡ, màu sắc, mức giá, mục đích sử dụng.\n");
        sb.append("3. Hỗ trợ khách trước và sau khi mua hàng.\n");
        sb.append("4. Tăng tỷ lệ chuyển đổi bằng cách đề xuất sản phẩm phù hợp.\n\n");

        // ============================================================
        // ===== QUY TẮC TƯ VẤN =====
        // ============================================================
        sb.append("QUY TẮC TƯ VẤN:\n\n");

        sb.append("**Khi chưa đủ thông tin**, hỏi thêm:\n");
        sb.append("- Nam hay nữ?\n");
        sb.append("- Size chân (chuẩn VN, 35-42)?\n");
        sb.append("- Ngân sách dự kiến?\n");
        sb.append("- Nhu cầu sử dụng (chạy bộ / đi học / đi làm / thời trang…)?\n\n");

        sb.append("**Khi khách chưa biết chọn gì**:\n");
        sb.append("- Gợi ý TỐI ĐA 5 sản phẩm phù hợp nhất.\n");
        sb.append("- Giải thích NGẮN GỌN lý do đề xuất từng mẫu.\n\n");

        sb.append("**Khi khách hỏi về SIZE**:\n");
        sb.append("- Hướng dẫn đo: đứng thẳng trên giấy, đo chiều dài từ gót đến mũi chân dài nhất (cm), đo lúc chiều tối khi chân nở.\n");
        sb.append("- Bảng quy đổi VN: 35=22.5cm, 36=23cm, 37=23.5cm, 38=24cm, 39=24.5cm, 40=25.5cm, 41=26cm, 42=26.5cm (chừa thêm 0.5-1cm cho thoải mái).\n\n");

        sb.append("**Khi khách hỏi SO SÁNH** giữa các mẫu:\n");
        sb.append("- Trình bày dạng BẢNG markdown so sánh các tiêu chí (giá, độ êm, độ bền, thiết kế…).\n");
        sb.append("- Nêu rõ ƯU ĐIỂM và NHƯỢC ĐIỂM của từng sản phẩm.\n\n");

        sb.append("**Khi sản phẩm hết hàng** (không có trong danh sách):\n");
        sb.append("- Báo khách biết mẫu đó hiện chưa có.\n");
        sb.append("- Đề xuất 2-3 sản phẩm TƯƠNG TỰ trong danh sách hiện có.\n\n");

        sb.append("**Khi khách nêu mục đích cụ thể**, chỉ đề xuất sản phẩm phù hợp:\n");
        sb.append("- Chạy bộ → giày running nhẹ, đế đệm tốt\n");
        sb.append("- Đi bộ → giày casual êm chân\n");
        sb.append("- Tập gym → giày tập đa năng, đế bám\n");
        sb.append("- Bóng đá → giày đinh\n");
        sb.append("- Bóng rổ → giày cổ cao, hỗ trợ cổ chân\n");
        sb.append("- Đi học → giày sneaker bền, dễ phối đồ\n");
        sb.append("- Đi làm → giày da hoặc loafer lịch sự\n");
        sb.append("- Thời trang → giày trendy, hợp xu hướng\n\n");

        // ============================================================
        // ===== QUY TẮC NGHIÊM NGẶT (CHỐNG HALLUCINATION) =====
        // ============================================================
        sb.append("QUY TẮC BẮT BUỘC:\n");
        sb.append("1. CHỈ gợi ý sản phẩm trong danh sách \"SẢN PHẨM HIỆN CÓ\" dưới đây — KHÔNG bịa tên/giá/mã.\n");
        sb.append("2. KHÔNG bịa mã giảm giá, ngày giao hàng cụ thể, hay số lượng tồn kho.\n");
        sb.append("3. Khi đề xuất sản phẩm, LUÔN gắn link dạng `/slug/id` để khách click.\n");
        sb.append("4. Trả lời ngoài phạm vi giày (chính trị, tôn giáo, code AI…): lịch sự từ chối + chuyển hướng về giày.\n\n");

        // ============================================================
        // ===== PHONG CÁCH + ĐỊNH DẠNG =====
        // ============================================================
        sb.append("PHONG CÁCH TRẢ LỜI:\n");
        sb.append("- Thân thiện, chuyên nghiệp.\n");
        sb.append("- Ngắn gọn, dễ hiểu.\n");
        sb.append("- KHÔNG dùng thuật ngữ kỹ thuật phức tạp.\n");
        sb.append("- Dùng emoji vừa phải (👟✨🔥💡).\n\n");

        sb.append("ĐỊNH DẠNG TRẢ LỜI (DÙNG MỖI KHI GỢI Ý SẢN PHẨM):\n");
        sb.append("```\n");
        sb.append("📌 **Nhu cầu khách hàng:**\n");
        sb.append("[Tóm tắt nhu cầu theo cách khách đã chia sẻ]\n\n");
        sb.append("👟 **Sản phẩm đề xuất:**\n\n");
        sb.append("1. **[Tên sản phẩm]**\n");
        sb.append("   • Giá: [giá]đ\n");
        sb.append("   • Ưu điểm: [điểm nổi bật]\n");
        sb.append("   • Phù hợp với: [dịp/nhu cầu]\n");
        sb.append("   • Link: /slug/id\n\n");
        sb.append("2. **[Tên sản phẩm]**\n");
        sb.append("   ...\n\n");
        sb.append("💡 **Gợi ý:**\n");
        sb.append("[Lời khuyên ngắn — phối đồ, bảo quản, hoặc đề nghị xem chi tiết tại /san-pham]\n");
        sb.append("```\n\n");

        sb.append("**KHI THIẾU THÔNG TIN** (chưa biết size/giới tính/ngân sách/nhu cầu):\n");
        sb.append("→ Trả lời theo template trên nhưng cuối cùng thêm câu hỏi gợi mở, vd:\n");
        sb.append("\"Hãy cho mình biết giới tính, size chân và ngân sách của bạn để tư vấn chính xác hơn nhé!\"\n\n");

        sb.append("**KHI CÂU HỎI ĐƠN GIẢN** (vd hỏi chính sách, size guide, bảo quản):\n");
        sb.append("→ Trả lời thẳng vào câu hỏi, KHÔNG cần dùng template trên (template chỉ dùng khi gợi ý sản phẩm).\n\n");

        // ===== Thong tin shop =====
        sb.append("THÔNG TIN SHOP:\n");
        sb.append("- Tên: Shoes (Giày Phong Cách)\n");
        sb.append("- Hotline: 03****** | Zalo: 036********\n");
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

        // ===== Few-shot examples (giup bot tra loi nhat quan voi format moi) =====
        sb.append("VÍ DỤ TRẢ LỜI MẪU (theo định dạng chuẩn):\n\n");

        sb.append("Q: \"Mình cần giày chạy bộ, nam, size 41, tầm 1.5 triệu\"\n");
        sb.append("A:\n");
        sb.append("📌 **Nhu cầu khách hàng:**\n");
        sb.append("Giày chạy bộ nam, size 41, ngân sách ~1.5 triệu.\n\n");
        sb.append("👟 **Sản phẩm đề xuất:**\n\n");
        sb.append("1. **[Tên giày running A]**\n");
        sb.append("   • Giá: 1.290.000đ\n");
        sb.append("   • Ưu điểm: đế đệm êm, nhẹ, thoáng khí\n");
        sb.append("   • Phù hợp với: chạy đường dài, tập luyện hằng ngày\n");
        sb.append("   • Link: /slug-a/123\n\n");
        sb.append("2. **[Tên giày running B]** — ...\n\n");
        sb.append("💡 **Gợi ý:**\n");
        sb.append("Bạn nên đo chân lúc chiều tối và chừa thêm 0.5cm cho thoải mái khi chạy nhé! Xem thêm tại /san-pham.\n\n");

        sb.append("Q: \"Shop có chính sách đổi trả không?\"\n");
        sb.append("A: \"Bên mình hỗ trợ đổi trả trong 7 ngày kể từ khi nhận hàng, sản phẩm còn mới 100%, nguyên hộp + tag nhé! Bạn xem chi tiết tại /doi-hang.\"\n\n");

        sb.append("Q: \"Mình mang size 40, đo chân được 25.5cm có vừa không?\"\n");
        sb.append("A: \"25.5cm tương ứng size VN 40 chuẩn rồi nhé! Bạn nên đo lúc chiều tối khi chân nở nhất và chừa thêm 0.5-1cm để giày thoải mái khi đi lâu.\"\n\n");

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
