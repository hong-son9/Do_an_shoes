package com.phs.application.controller.shop;

import com.phs.application.entity.Brand;
import com.phs.application.entity.Category;
import com.phs.application.model.dto.PageableDTO;
import com.phs.application.model.dto.ProductInfoDTO;
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

    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        Map<String, Object> resp = new HashMap<>();
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
            resp.put("reply", "Hiện chatbot đang gặp sự cố kết nối. Bạn vui lòng thử lại sau ít phút hoặc gọi hotline 036 545 4643 để được hỗ trợ trực tiếp nhé!");
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            resp.put("reply", "Đã có lỗi xảy ra. Bạn vui lòng thử lại sau hoặc gọi hotline 036 545 4643.");
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

    /* ========== System prompt with live DB context ========== */

    private String buildSystemPrompt(String userMessage, List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();

        sb.append("Bạn là \"Sneaker Bot\" — trợ lý tư vấn của cửa hàng giày Sơn Shoes (Giày Phong Cách - Phí Hồng Sơn).\n");
        sb.append("QUY TẮC TRẢ LỜI:\n");
        sb.append("1. Luôn dùng tiếng Việt thân thiện, ngắn gọn, không quá 6 câu mỗi lần.\n");
        sb.append("2. CHỈ tư vấn sản phẩm có trong danh sách \"SẢN PHẨM HIỆN CÓ\" ở dưới. KHÔNG bịa tên/giá sản phẩm khác.\n");
        sb.append("3. Khi gợi ý sản phẩm cụ thể, LUÔN kèm tên + giá + đường link dạng /slug/id (tự lấy từ danh sách bên dưới).\n");
        sb.append("4. Nếu khách hỏi mẫu nằm ngoài danh sách: nói rằng hiện chưa có hoặc đề nghị xem thêm tại /san-pham.\n");
        sb.append("5. Nếu khách hỏi giá khuyến mãi, mã giảm giá cụ thể: chỉ trả lời theo giá đã ghi, không tự bịa code.\n");
        sb.append("6. Trả lời ngoài phạm vi giày (chính trị, tôn giáo, kỹ thuật khác): lịch sự chuyển hướng về mua giày.\n\n");

        sb.append("THÔNG TIN CỬA HÀNG:\n");
        sb.append("- Tên: Sơn Shoes (Giày Phong Cách - Phí Hồng Sơn)\n");
        sb.append("- Hotline: 036 545 4643 | Zalo: 0365454643\n");
        sb.append("- Địa chỉ: 8 ngõ 7, Nguyên Xá, Minh Khai, Hà Nội\n");
        sb.append("- Giờ làm việc: T2 - CN, 9:00 - 20:00\n");
        sb.append("- Size có sẵn: 35 - 42 (chuẩn VN)\n\n");

        // Brands & categories
        List<Brand> brands = safeBrands();
        List<Category> categories = safeCategories();
        if (!brands.isEmpty()) {
            sb.append("THƯƠNG HIỆU ĐANG BÁN: ");
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

        // Determine which product set to fetch based on user message + recent history
        String contextText = (userMessage + " " + recentUserText(history)).toLowerCase();
        Map<String, ProductInfoDTO> picked = new LinkedHashMap<>();

        // 1. If message mentions a brand name, fetch matching products
        for (Brand b : brands) {
            if (b.getName() == null) continue;
            String bn = b.getName().toLowerCase();
            if (contextText.contains(bn)) {
                addProducts(picked, safeSearch(b.getName()), 8);
            }
        }

        // 2. If message mentions intent words, fetch corresponding lists
        boolean wantsBestSeller =
                contextText.contains("bán chạy") || contextText.contains("ban chay") ||
                contextText.contains("hot") || contextText.contains("nhiều người mua") ||
                contextText.contains("best seller") || contextText.contains("phổ biến");
        boolean wantsNew =
                contextText.contains("mới") || contextText.contains("moi nhat") ||
                contextText.contains("mới nhất") || contextText.contains("new") ||
                contextText.contains("vừa về");
        boolean wantsCheap =
                contextText.contains("rẻ") || contextText.contains("re") ||
                contextText.contains("giá thấp") || contextText.contains("tiết kiệm") ||
                contextText.contains("budget");

        if (wantsBestSeller) addProducts(picked, safeBest(), 8);
        if (wantsNew)        addProducts(picked, safeNew(), 8);

        // 3. Fallback: always include some defaults so bot has something to recommend
        if (picked.isEmpty()) {
            addProducts(picked, safeNew(), 5);
            addProducts(picked, safeBest(), 5);
        }

        // 4. If wants cheap, sort the picked list by effective price ascending
        List<ProductInfoDTO> productList = new ArrayList<>(picked.values());
        if (wantsCheap) {
            productList.sort(Comparator.comparingLong(this::effectivePrice));
        }

        // Cap to 12 products to keep prompt small
        if (productList.size() > 12) productList = productList.subList(0, 12);

        sb.append("SẢN PHẨM HIỆN CÓ (tham khảo, dùng để tư vấn cho khách):\n");
        if (productList.isEmpty()) {
            sb.append("(Hiện chưa lấy được danh sách sản phẩm)\n");
        } else {
            for (ProductInfoDTO p : productList) {
                sb.append("- ").append(p.getName());
                long eff = effectivePrice(p);
                sb.append(" | Giá: ").append(formatVnd(eff)).append("đ");
                if (p.getPromotionPrice() > 0 && p.getPromotionPrice() < p.getPrice()) {
                    sb.append(" (giảm từ ").append(formatVnd(p.getPrice())).append("đ)");
                }
                if (p.getTotalSold() > 0) {
                    sb.append(" | Đã bán: ").append(p.getTotalSold());
                }
                sb.append(" | Link: /").append(p.getSlug()).append("/").append(p.getId()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("CHÍNH SÁCH:\n");
        sb.append("- Đổi trả trong 7 ngày kể từ ngày nhận hàng, sản phẩm còn mới 100%.\n");
        sb.append("- Miễn phí ship đơn từ 500.000đ. Miễn ship nếu lỗi do nhà sản xuất.\n");
        sb.append("- Thanh toán: COD, VNPay, chuyển khoản, Momo.\n\n");

        sb.append("LIÊN KẾT HỮU ÍCH:\n");
        sb.append("- /san-pham : danh sách toàn bộ sản phẩm + filter\n");
        sb.append("- /tin-tuc : tin tức & xu hướng giày\n");
        sb.append("- /huong-dan : hướng dẫn mua hàng\n");
        sb.append("- /doi-hang : chính sách đổi trả\n");
        sb.append("- /lien-he : liên hệ & bản đồ cửa hàng\n");

        return sb.toString();
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
