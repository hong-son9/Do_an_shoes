package com.phs.application.controller.admin;

import com.phs.application.entity.User;
import com.phs.application.repository.OrderRepository;
import com.phs.application.security.CustomUserDetails;
import com.phs.application.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private OrderRepository orderRepository;

    @GetMapping("/admin/users")
    public String homePages(Model model,
                            @RequestParam(defaultValue = "", required = false) String fullName,
                            @RequestParam(defaultValue = "", required = false) String phone,
                            @RequestParam(defaultValue = "", required = false) String email,
                            @RequestParam(defaultValue = "", required = false) String address,
                            @RequestParam(defaultValue = "1", required = false) Integer page) {
        Page<User> users = userService.adminListUserPages(fullName, phone, email, page);
        model.addAttribute("users", users.getContent());
        model.addAttribute("totalPages", users.getTotalPages());
        model.addAttribute("currentPage", users.getPageable().getPageNumber() + 1);
        return "admin/user/list";
    }

    @GetMapping("/api/admin/users/list")
    public ResponseEntity<Object> getListUserPages(@RequestParam(defaultValue = "", required = false) String fullName,
                                                   @RequestParam(defaultValue = "", required = false) String phone,
                                                   @RequestParam(defaultValue = "", required = false) String email,
                                                   @RequestParam(defaultValue = "", required = false) String address,
                                                   @RequestParam(defaultValue = "1", required = false) Integer page) {
        Page<User> users = userService.adminListUserPages(fullName, phone, email, page);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/api/admin/users/{id}/status")
    public ResponseEntity<Object> toggleUserStatus(@PathVariable long id) {
        long currentAdminId = getCurrentUserId();
        User updated = userService.toggleUserStatus(id, currentAdminId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", updated.getId());
        resp.put("status", updated.isStatus());
        resp.put("message", updated.isStatus() ? "Đã mở khóa tài khoản" : "Đã khóa tài khoản");
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/api/admin/users/{id}/role")
    public ResponseEntity<Object> toggleAdminRole(@PathVariable long id) {
        long currentAdminId = getCurrentUserId();
        User updated = userService.toggleAdminRole(id, currentAdminId);
        boolean isAdmin = updated.getRoles() != null && updated.getRoles().contains("ADMIN");
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", updated.getId());
        resp.put("isAdmin", isAdmin);
        resp.put("message", isAdmin ? "Đã cấp quyền quản trị viên" : "Đã thu hồi quyền quản trị viên");
        return ResponseEntity.ok(resp);
    }

    private long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) auth.getPrincipal()).getUser().getId();
        }
        return -1L;
    }

    /** Tra ve 15 don hang gan nhat de hien thi notification bell admin. */
    @GetMapping("/api/admin/orders/recent")
    public ResponseEntity<Object> getRecentOrders() {
        List<Object[]> rows = orderRepository.getRecentOrdersRaw();
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0] != null ? ((Number) r[0]).longValue() : null);
            m.put("createdAt", r[1] != null ? ((Timestamp) r[1]).getTime() : null);
            m.put("status", r[2] != null ? ((Number) r[2]).intValue() : null);
            m.put("quantity", r[3] != null ? ((Number) r[3]).intValue() : null);
            m.put("buyerName", r[4] != null ? r[4].toString() : "Khách");
            m.put("productName", r[5] != null ? r[5].toString() : "Sản phẩm");
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }
}
