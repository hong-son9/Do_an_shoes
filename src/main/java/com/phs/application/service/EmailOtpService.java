package com.phs.application.service;

import com.phs.application.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service quan ly OTP gui qua email cho dang ky tai khoan.
 *
 * Cache trong RAM (Map<email, OtpRecord>) — du dung cho 1 instance.
 * Neu deploy multi-node, can thay bang Redis.
 */
@Service
public class EmailOtpService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    private final Map<String, OtpRecord> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public static final String PURPOSE_REGISTER = "register";
    public static final String PURPOSE_RESET = "reset";

    /** Default: dang ky tai khoan. */
    public void sendOtp(String email) {
        sendOtp(email, PURPOSE_REGISTER);
    }

    /**
     * Gui OTP cho 1 muc dich cu the (register/reset).
     * Cache key = purpose:email → 2 luong khong xung dot.
     */
    public void sendOtp(String email, String purpose) {
        if (email == null || email.trim().isEmpty()) {
            throw new BadRequestException("Email trống");
        }
        String normalized = email.trim().toLowerCase();
        String key = purpose + ":" + normalized;
        long now = System.currentTimeMillis();

        // Check cooldown: tranh spam send
        OtpRecord prev = store.get(key);
        if (prev != null && now - prev.createdAt < resendCooldownSeconds * 1000L) {
            long wait = resendCooldownSeconds - (now - prev.createdAt) / 1000L;
            throw new BadRequestException("Vui lòng chờ " + wait + " giây trước khi gửi lại OTP");
        }

        String otp = generateOtp();
        OtpRecord record = new OtpRecord(otp, now, now + expiryMinutes * 60_000L);
        store.put(key, record);

        // Gui email (neu mail da cau hinh)
        if (mailSender != null && fromEmail != null && !fromEmail.isEmpty()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(normalized);
                if (PURPOSE_RESET.equals(purpose)) {
                    message.setSubject("[Shoes] Mã xác thực đặt lại mật khẩu");
                    message.setText(buildResetEmailBody(otp));
                } else {
                    message.setSubject("[Shoes] Mã xác thực đăng ký tài khoản");
                    message.setText(buildEmailBody(otp));
                }
                mailSender.send(message);
            } catch (Exception e) {
                throw new BadRequestException("Không gửi được email — vui lòng kiểm tra lại địa chỉ hoặc thử lại sau.");
            }
        } else {
            System.out.println("[DEV] OTP for " + key + " = " + otp + " (mail not configured)");
        }
    }

    /** Default: dang ky tai khoan. */
    public boolean verifyOtp(String email, String inputOtp) {
        return verifyOtp(email, inputOtp, PURPOSE_REGISTER);
    }

    /**
     * Verify OTP theo purpose. Tra ve true neu match va chua het han.
     * Xoa OTP sau khi verify thanh cong de tranh dung lai.
     */
    public boolean verifyOtp(String email, String inputOtp, String purpose) {
        if (email == null || inputOtp == null) return false;
        String normalized = email.trim().toLowerCase();
        String key = purpose + ":" + normalized;
        OtpRecord record = store.get(key);
        if (record == null) return false;
        long now = System.currentTimeMillis();
        if (now > record.expiresAt) {
            store.remove(key);
            return false;
        }
        if (!record.otp.equals(inputOtp.trim())) {
            return false;
        }
        store.remove(key);
        return true;
    }

    private String generateOtp() {
        int n = 100_000 + random.nextInt(900_000);
        return String.valueOf(n);
    }

    private String buildEmailBody(String otp) {
        return "Xin chào,\n\n"
                + "Mã xác thực đăng ký tài khoản Shoes của bạn là:\n\n"
                + "    " + otp + "\n\n"
                + "Mã có hiệu lực trong " + expiryMinutes + " phút.\n"
                + "Nếu bạn không yêu cầu đăng ký, vui lòng bỏ qua email này.\n\n"
                + "—\n"
                + "Shoes — Giày Phong Cách\n"
                + "Hotline: 036 XXX XXXX";
    }

    private String buildResetEmailBody(String otp) {
        return "Xin chào,\n\n"
                + "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản Shoes của bạn.\n\n"
                + "Mã xác thực:\n\n"
                + "    " + otp + "\n\n"
                + "Mã có hiệu lực trong " + expiryMinutes + " phút.\n\n"
                + "⚠ Nếu bạn KHÔNG yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này\n"
                + "  và cân nhắc đổi mật khẩu để bảo vệ tài khoản.\n\n"
                + "—\n"
                + "Shoes — Giày Phong Cách\n"
                + "Hotline: 036 XXX XXXX";
    }

    private static class OtpRecord {
        final String otp;
        final long createdAt;
        final long expiresAt;
        OtpRecord(String otp, long createdAt, long expiresAt) {
            this.otp = otp;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }
}
