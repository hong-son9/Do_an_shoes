package com.phs.application.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Bat error tu OAuth2 flow va log day du de debug. Redirect ve / kem ma loi chi tiet
 * de frontend toastr hien thi thong bao co ich.
 */
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                         HttpServletResponse response,
                                         AuthenticationException exception) throws IOException, ServletException {
        String errorCode = "auth_failed";
        String errorDescription = exception.getMessage();
        String provider = detectProvider(request);

        if (exception instanceof OAuth2AuthenticationException) {
            OAuth2Error err = ((OAuth2AuthenticationException) exception).getError();
            errorCode = err.getErrorCode() != null ? err.getErrorCode() : errorCode;
            errorDescription = err.getDescription() != null ? err.getDescription() : errorDescription;
        }

        log.error("[OAuth2 Login FAILED] provider={}, errorCode={}, description={}",
                provider, errorCode, errorDescription, exception);

        String enc = URLEncoder.encode(errorDescription != null ? errorDescription : "Unknown",
                StandardCharsets.UTF_8.toString());
        String redirectUrl = "/?oauthError=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8.toString())
                + "&provider=" + URLEncoder.encode(provider, StandardCharsets.UTF_8.toString())
                + "&desc=" + enc;
        response.sendRedirect(redirectUrl);
    }

    private String detectProvider(HttpServletRequest request) {
        // URL dang /login/oauth2/code/{provider} → lay phan cuoi
        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/oauth2/code/")) {
            String[] parts = uri.split("/");
            return parts[parts.length - 1];
        }
        return "unknown";
    }
}
