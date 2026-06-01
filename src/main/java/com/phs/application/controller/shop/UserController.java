package com.phs.application.controller.shop;

import com.phs.application.config.Contant;
import com.phs.application.entity.User;
import com.phs.application.exception.BadRequestException;
import com.phs.application.model.dto.UserDTO;
import com.phs.application.model.mapper.UserMapper;
import com.phs.application.model.request.ChangePasswordRequest;
import com.phs.application.model.request.CreateUserRequest;
import com.phs.application.model.request.LoginRequest;
import com.phs.application.model.request.ResetPasswordRequest;
import com.phs.application.model.request.UpdateProfileRequest;
import com.phs.application.security.CustomUserDetails;
import com.phs.application.security.JwtTokenUtil;
import com.phs.application.service.EmailOtpService;
import com.phs.application.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;

@Controller
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private EmailOtpService emailOtpService;

    @GetMapping("/users")
    public ResponseEntity<Object> getListUsers() {
        List<UserDTO> userDTOS = userService.getListUsers();
        return ResponseEntity.ok(userDTOS);
    }

    @PostMapping("/api/admin/users")
    public ResponseEntity<Object> createUser(@Valid @RequestBody CreateUserRequest createUserRequest){
        User user = userService.createUser(createUserRequest);
        return ResponseEntity.ok(UserMapper.toUserDTO(user));
    }

    @PostMapping("/api/forgot-password/send-otp")
    public ResponseEntity<Object> sendResetOtp(@RequestBody java.util.Map<String, String> body) {
        String rawEmail = body.get("email");
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            throw new BadRequestException("Email trống");
        }
        String email = rawEmail.trim();
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BadRequestException("Email không đúng định dạng");
        }
        // Email phai ton tai trong he thong moi gui OTP — KHONG toLowerCase de tranh
        // case-sensitive mismatch voi data luc dang ky.
        if (!userService.existsByEmail(email)) {
            // Du dev khong thay OTP → bao lỗi rõ ràng giúp debug. Production có thể đổi thành
            // im lặng tra "success" để chống enumerate.
            throw new BadRequestException("Email chưa được đăng ký trong hệ thống");
        }
        emailOtpService.sendOtp(email, com.phs.application.service.EmailOtpService.PURPOSE_RESET);
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("success", true);
        resp.put("message", "Mã OTP đã được gửi tới " + email + ". Mã có hiệu lực 5 phút.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/forgot-password/reset")
    public ResponseEntity<Object> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        if (!emailOtpService.verifyOtp(req.getEmail(), req.getOtp(),
                com.phs.application.service.EmailOtpService.PURPOSE_RESET)) {
            throw new BadRequestException("Mã OTP không đúng hoặc đã hết hạn");
        }
        userService.resetPasswordByEmail(req.getEmail(), req.getNewPassword());
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("success", true);
        resp.put("message", "Đặt lại mật khẩu thành công. Vui lòng đăng nhập với mật khẩu mới.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/register/send-otp")
    public ResponseEntity<Object> sendRegisterOtp(@RequestBody java.util.Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.trim().isEmpty()) {
            throw new BadRequestException("Email trống");
        }
        // Email format check don gian
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BadRequestException("Email không đúng định dạng");
        }
        // Kiem tra email da ton tai chua
        if (userService.existsByEmail(email.trim().toLowerCase())) {
            throw new BadRequestException("Email đã được sử dụng — vui lòng đăng nhập");
        }
        emailOtpService.sendOtp(email);
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("success", true);
        resp.put("message", "Mã OTP đã được gửi tới " + email + ". Mã có hiệu lực 5 phút.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/register")
    public ResponseEntity<Object> register(@Valid @RequestBody CreateUserRequest createUserRequest, HttpServletResponse response) {
        // Verify OTP truoc khi tao tai khoan
        String otp = createUserRequest.getOtp();
        if (otp == null || otp.trim().isEmpty()) {
            throw new BadRequestException("Vui lòng nhập mã OTP đã gửi về email");
        }
        if (!emailOtpService.verifyOtp(createUserRequest.getEmail(), otp)) {
            throw new BadRequestException("Mã OTP không đúng hoặc đã hết hạn — vui lòng gửi lại");
        }

        //Create user
        User user = userService.createUser(createUserRequest);

        //Gen token
        UserDetails principal = new CustomUserDetails(user);
        String token = jwtTokenUtil.generateToken(principal);

        //Add token on cookie to login (HttpOnly + SameSite=Lax de chong CSRF + XSS)
        addJwtCookie(response, token);

        return ResponseEntity.ok(UserMapper.toUserDTO(user));
    }

    private void addJwtCookie(HttpServletResponse response, String token) {
        // Dung Set-Cookie header thay vi Cookie object de set duoc SameSite
        String cookieValue = String.format(
                "JWT_TOKEN=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax",
                token, Contant.MAX_AGE_COOKIE
        );
        response.addHeader("Set-Cookie", cookieValue);
    }

    @PostMapping("/api/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        //Authenticate
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail(),
                    loginRequest.getPassword()
            ));
            //Gen token
            String token = jwtTokenUtil.generateToken((CustomUserDetails) authentication.getPrincipal());

            //Add token to cookie to login (HttpOnly + SameSite=Lax)
            addJwtCookie(response, token);

            return ResponseEntity.ok(UserMapper.toUserDTO(((CustomUserDetails) authentication.getPrincipal()).getUser()));
        } catch (Exception ex) {
            throw new BadRequestException("Email hoặc mật khẩu không chính xác!");

        }
    }

    @GetMapping("/tai-khoan")
    public String getProfilePage(Model model) {
        return "shop/account";
    }

    @PostMapping("/api/change-password")
    public ResponseEntity<Object> changePassword(@Valid @RequestBody ChangePasswordRequest passwordReq) {
        User user = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
        userService.changePassword(user, passwordReq);
        return ResponseEntity.ok("Đổi mật khẩu thành công");
    }

    @PutMapping("/api/update-profile")
    public ResponseEntity<Object> updateProfile(@Valid @RequestBody UpdateProfileRequest profileReq) {
        User user = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();

        user = userService.updateProfile(user, profileReq);
        UserDetails userDetails = new CustomUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return ResponseEntity.ok("Cập nhật thành công");
    }
}
