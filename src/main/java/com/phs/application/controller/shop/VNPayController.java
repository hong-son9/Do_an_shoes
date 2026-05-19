package com.phs.application.controller.shop;

import com.phs.application.entity.Order;
import com.phs.application.exception.BadRequestException;
import com.phs.application.service.OrderService;
import com.phs.application.service.VNPayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Controller
public class VNPayController {

    @Autowired
    private VNPayService vnPayService;

    @Autowired
    private OrderService orderService;

    @PostMapping("/api/payment/vnpay/create")
    public ResponseEntity<Object> createPayment(@RequestParam("order_id") long orderId,
                                                HttpServletRequest request) {
        Order order = orderService.findOrderById(orderId);
        long amount = order.getTotalPrice();
        if (amount <= 0) {
            throw new BadRequestException("Số tiền thanh toán không hợp lệ");
        }
        String orderInfo = "Thanh toan don hang #" + orderId;
        String paymentUrl = vnPayService.createPaymentUrl(orderId, amount, orderInfo, request);

        Map<String, String> resp = new HashMap<>();
        resp.put("paymentUrl", paymentUrl);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/payment/vnpay/return")
    public String paymentReturn(HttpServletRequest request, Model model) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            params.put(name, request.getParameter(name));
        }

        boolean validSignature = vnPayService.verifyReturn(params);
        String responseCode = params.get("vnp_ResponseCode");
        long orderId = vnPayService.extractOrderId(params.get("vnp_TxnRef"));
        boolean success = validSignature && "00".equals(responseCode);

        long amountDisplay = 0L;
        try {
            amountDisplay = Long.parseLong(params.getOrDefault("vnp_Amount", "0")) / 100L;
        } catch (NumberFormatException ignored) {
        }

        model.addAttribute("success", success);
        model.addAttribute("validSignature", validSignature);
        model.addAttribute("responseCode", responseCode);
        model.addAttribute("orderId", orderId);
        model.addAttribute("amount", amountDisplay);
        model.addAttribute("bankCode", params.getOrDefault("vnp_BankCode", ""));
        model.addAttribute("transactionNo", params.getOrDefault("vnp_TransactionNo", ""));
        model.addAttribute("payDate", params.getOrDefault("vnp_PayDate", ""));
        return "shop/vnpay-return";
    }
}
