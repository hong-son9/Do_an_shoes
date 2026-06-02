package com.phs.application.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
@Configuration
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private AuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private JwtRequestFillter jwtRequestFillter;

    @Autowired
    private JwtUserDetailsService jwtUserDetailsService;

    @Autowired
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @Autowired
    private OAuth2LoginFailureHandler oauth2LoginFailureHandler;

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Autowired
    protected void configGlobal(AuthenticationManagerBuilder auth) throws Exception{
        auth.userDetailsService(jwtUserDetailsService).passwordEncoder(passwordEncoder());
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception{
        return super.authenticationManagerBean();
    }

    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception{
        // CSRF disabled toan cuc:
        // Project dung JWT stateless (Authorization header / cookie). Cac form HTML
        // se gui thong qua AJAX voi JWT, khong dung session cookie + form post truyen thong.
        // De bao mat CSRF du an JWT-cookie, can set SameSite=Lax/Strict tren JWT_TOKEN
        // (xem JwtRequestFillter / login flow).
        httpSecurity
                .cors()
                .and()
                .csrf()
                .disable()
                .authorizeRequests()
                .antMatchers("/api/cart/count").permitAll()
                .antMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .antMatchers("/api/order", "/tai-khoan", "/tai-khoan/**", "/api/change-password", "/api/update-profile",
                        "/api/update-avatar", "/api/orders/my-status", "/gio-hang", "/api/cart/**").authenticated()
                .antMatchers("/admin/**","/api/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()
                .and()
                .oauth2Login()
                .successHandler(oauth2LoginSuccessHandler)
                .failureHandler(oauth2LoginFailureHandler)
                .and()
                .logout()
                .logoutUrl("/api/logout")
                .logoutSuccessUrl("/")
                .deleteCookies("JWT_TOKEN")
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .and()
                // IF_REQUIRED: OAuth2 redirect dance can session de luu state.
                // JWT van hoat dong binh thuong vi JwtRequestFillter doc cookie JWT_TOKEN
                // va set SecurityContext khong dua vao session.
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .and()
                .headers()
                // Bao ve XSS/clickjacking co ban
                .frameOptions().sameOrigin()
                .xssProtection().and()
                .contentTypeOptions().and()
                .referrerPolicy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)
                .and()
                .and()
                .addFilterBefore(jwtRequestFillter, UsernamePasswordAuthenticationFilter.class);
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web
                .ignoring()
                .antMatchers("/css/**", "/script/**", "/image/**", "/vendor/**", "/favicon.ico", "/adminlte/**", "/shop/**", "/media/static/**");
    }
}
