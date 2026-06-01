package com.phs.application.security;

import com.phs.application.config.Contant;
import com.phs.application.entity.User;
import com.phs.application.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Sau khi Google OAuth2 thanh cong:
 *  1. Lay email + ten tu OAuth2User
 *  2. Tim hoac tao user trong DB
 *  3. Sinh JWT, set vao cookie (HttpOnly + SameSite=Lax)
 *  4. Redirect ve trang chu
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            String email = (String) oauth2User.getAttributes().get("email");
            String name = (String) oauth2User.getAttributes().get("name");
            if (email == null || email.isEmpty()) {
                response.sendRedirect("/?oauthError=missing_email");
                return;
            }
            User user = userService.findOrCreateOAuthUser(email, name);

            // Tai khoan bi admin khoa → don sach OAuth session + SecurityContext truoc khi redirect.
            // Neu khong, principal DefaultOAuth2User van ton tai trong session → cac request sau
            // se crash khi cac controller/interceptor cast sang CustomUserDetails.
            if (!user.isStatus()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.sendRedirect("/?oauthError=account_locked");
                return;
            }

            // Sinh JWT giong nhu /api/login
            CustomUserDetails principal = new CustomUserDetails(user);
            String token = jwtTokenUtil.generateToken(principal);

            // Set cookie JWT (HttpOnly + SameSite=Lax)
            String cookieValue = String.format(
                    "JWT_TOKEN=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax",
                    token, Contant.MAX_AGE_COOKIE
            );
            response.addHeader("Set-Cookie", cookieValue);

            // Redirect ve trang chu
            response.sendRedirect("/?oauth=success");
        } catch (Exception e) {
            response.sendRedirect("/?oauthError=" + java.net.URLEncoder.encode(
                    e.getMessage() != null ? e.getMessage() : "unknown",
                    java.nio.charset.StandardCharsets.UTF_8.toString()));
        }
    }
}
