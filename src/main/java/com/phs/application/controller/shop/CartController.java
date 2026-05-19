package com.phs.application.controller.shop;

import com.phs.application.entity.Order;
import com.phs.application.entity.User;
import com.phs.application.model.dto.CartItemDTO;
import com.phs.application.model.request.AddCartItemRequest;
import com.phs.application.model.request.CheckoutCartRequest;
import com.phs.application.model.request.UpdateCartItemRequest;
import com.phs.application.security.CustomUserDetails;
import com.phs.application.service.CartService;
import com.phs.application.service.VNPayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    @Autowired
    private VNPayService vnPayService;

    @GetMapping("/gio-hang")
    public String cartPage(Model model) {
        long userId = currentUserId();
        List<CartItemDTO> items = cartService.getCartItems(userId);
        long total = cartService.getTotalAmount(userId);
        User user = currentUser();
        model.addAttribute("items", items);
        model.addAttribute("totalAmount", total);
        model.addAttribute("itemCount", items.size());
        model.addAttribute("user_fullname", user.getFullName());
        model.addAttribute("user_phone", user.getPhone());
        model.addAttribute("user_address", user.getAddress());
        return "shop/cart";
    }

    @GetMapping("/api/cart")
    public ResponseEntity<Object> listItems() {
        long userId = currentUserId();
        Map<String, Object> resp = new HashMap<>();
        resp.put("items", cartService.getCartItems(userId));
        resp.put("totalAmount", cartService.getTotalAmount(userId));
        resp.put("itemCount", cartService.getItemCount(userId));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/api/cart/count")
    public ResponseEntity<Object> count() {
        try {
            long userId = currentUserId();
            Map<String, Object> resp = new HashMap<>();
            resp.put("count", cartService.getItemCount(userId));
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("count", 0);
            return ResponseEntity.ok(resp);
        }
    }

    @PostMapping("/api/cart/items")
    public ResponseEntity<Object> addItem(@Valid @RequestBody AddCartItemRequest request) {
        long userId = currentUserId();
        return ResponseEntity.ok(cartService.addItem(userId, request));
    }

    @PutMapping("/api/cart/items/{id}")
    public ResponseEntity<Object> updateItem(@PathVariable("id") long id,
                                             @Valid @RequestBody UpdateCartItemRequest request) {
        long userId = currentUserId();
        return ResponseEntity.ok(cartService.updateItem(userId, id, request));
    }

    @DeleteMapping("/api/cart/items/{id}")
    public ResponseEntity<Object> removeItem(@PathVariable("id") long id) {
        long userId = currentUserId();
        cartService.removeItem(userId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/cart/checkout")
    public ResponseEntity<Object> checkout(@Valid @RequestBody CheckoutCartRequest request,
                                           HttpServletRequest httpRequest) {
        long userId = currentUserId();
        List<Order> orders = cartService.checkout(userId, request);

        List<Long> orderIds = new ArrayList<>();
        long total = 0L;
        for (Order o : orders) {
            orderIds.add(o.getId());
            total += o.getTotalPrice();
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("orderIds", orderIds);
        resp.put("totalAmount", total);

        boolean useVnpay = "vnpay".equalsIgnoreCase(request.getPaymentMethod());
        if (useVnpay && !orders.isEmpty()) {
            long firstOrderId = orders.get(0).getId();
            String orderInfo = "Thanh toan gio hang " + firstOrderId;
            String paymentUrl = vnPayService.createPaymentUrl(firstOrderId, total, orderInfo, httpRequest);
            resp.put("paymentUrl", paymentUrl);
            // KHONG clear cart o day. Doi VNPay return success thi clear (xem VNPayController.paymentReturn).
        } else {
            // COD: thanh toan truc tiep coi nhu da xac nhan, clear cart ngay.
            cartService.clearCart(userId);
        }
        return ResponseEntity.ok(resp);
    }

    private long currentUserId() {
        return currentUser().getId();
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ((CustomUserDetails) principal).getUser();
    }
}
