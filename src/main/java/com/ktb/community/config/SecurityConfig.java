/*
 * [JWT 마이그레이션] Spring Security 최소 설정
 *
 * 목적:
 * - 기본 formLogin/httpBasic 비활성화 (JwtAuthenticationFilter가 인증 처리)
 * - CORS 설정 유지 (Cross-Origin 지원)
 * - PasswordEncoder는 PasswordEncoderConfig에서 제공
 *
 * 인증 처리:
 * - JwtAuthenticationFilter가 모든 인증 처리
 * - Spring Security는 CORS/CSRF만 관리
 */

package com.ktb.community.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 다중 origin 지원 (개발 + EC2)
        List<String> allowedOrigins = new java.util.ArrayList<>();
        allowedOrigins.add(frontendUrl);  // 기본 URL (포트 포함)
        
        // 포트 제거 버전 추가 (iptables 포트 포워딩 대응)
        String baseUrl = frontendUrl.replaceAll(":\\d+$", "");
        if (!baseUrl.equals(frontendUrl)) {
            allowedOrigins.add(baseUrl);
        }
        
        // localhost 개발 환경 추가 (EC2 배포 시에도 로컬 테스트 가능)
        if (!frontendUrl.contains("localhost")) {
            allowedOrigins.add("http://localhost:3000");
            allowedOrigins.add("http://localhost");
        }

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)  // 기본 로그인 폼 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)  // HTTP Basic 인증 비활성화
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()  // JwtAuthenticationFilter가 인증 처리
                );

        return http.build();
    }
}
