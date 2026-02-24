package com.ktb.community.controller;

import com.ktb.community.config.RateLimit;
import com.ktb.community.dto.ApiResponse;
import com.ktb.community.dto.request.LoginRequest;
// [세션 전환] JWT 방식 (미사용)
// import com.ktb.community.dto.request.RefreshTokenRequest;
// import com.ktb.community.dto.request.SignupRequest;  // 회원가입은 UserController에서 처리
import com.ktb.community.dto.response.AuthResponse;
import com.ktb.community.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
// import org.springframework.http.HttpStatus;  // 미사용
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 컨트롤러
 * API.md Section 1 참조
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * 로그인 (API.md Section 1.1)
     * POST /auth/login
     * Tier 1: 강한 제한 (brute-force 방지)
     * httpOnly Cookie 방식으로 토큰 전달 (XSS 방어)
     */
    @PostMapping("/login")
    @RateLimit(requestsPerMinute = 5)
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthService.AuthResult result = authService.login(request);

        // [세션 방식] (보존)
        // setCookie(response, "SESSIONID", result.sessionId(), 3600, "/");

        // [JWT 방식] RT → httpOnly Cookie (7일, Path=/auth)
        setCookie(response, "refresh_token", result.refreshToken(),
                  7 * 24 * 3600, "/auth");

        // AT + 사용자 정보 → 응답 body
        AuthResponse authResponse = AuthResponse.from(result.user(), result.accessToken());
        return ResponseEntity.ok(ApiResponse.success("login_success", authResponse));
    }
    
    /**
     * 로그아웃 (API.md Section 1.2)
     * POST /auth/logout
     * Cookie에서 RT 추출 및 삭제
     * Tier 3: 제한 없음 (공격 동인 없음)
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        // [세션 방식] (보존)
        // String sessionId = extractCookie(request, "SESSIONID");
        // if (sessionId != null) {
        //     authService.logout(sessionId);
        // }

        // [JWT 방식] RT 추출 및 삭제
        String refreshToken = extractCookie(request, "refresh_token");
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        // RT 쿠키 삭제 (MaxAge=0)
        Cookie rtCookie = new Cookie("refresh_token", null);
        rtCookie.setMaxAge(0);
        rtCookie.setPath("/auth");
        response.addCookie(rtCookie);

        return ResponseEntity.ok(ApiResponse.success("logout_success"));
    }
    
    /**
     * 액세스 토큰 재발급 (API.md Section 1.3)
     * POST /auth/refresh_token
     * Cookie에서 Refresh Token 추출하여 새 Access Token 발급
     * 사용자 정보도 함께 반환 (프론트엔드 동기화용)
     * Tier 2: 중간 제한 (비정상 토큰 갱신 감지)
     */
    @PostMapping("/refresh_token")
    @RateLimit(requestsPerMinute = 30)
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            HttpServletRequest request) {

        // RT 추출 (Cookie)
        String refreshToken = extractCookie(request, "refresh_token");
        if (refreshToken == null) {
            throw new IllegalArgumentException("No refresh token found");
        }

        // AT 재발급 (RT는 재사용)
        AuthService.AuthResult result = authService.refreshAccessToken(refreshToken);

        // AT + 사용자 정보 → 응답 body
        AuthResponse authResponse = AuthResponse.from(result.user(), result.accessToken());
        return ResponseEntity.ok(ApiResponse.success("token_refreshed", authResponse));
    }

    /**
     * Guest Token 발급 (회원가입용)
     * GET /auth/guest-token
     *
     * 용도: 회원가입 시 프로필 이미지 업로드를 위한 임시 토큰
     * - JWT가 없으면 Lambda 이미지 업로드 불가
     * - 회원가입 완료 후 정식 AT/RT로 교체
     *
     * 특징:
     * - 유효기간: 5분
     * - subject: "0" (게스트 전용 ID)
     * - role: GUEST
     * - Refresh Token 없음 (일회용)
     * - Rate Limit 없음 (회원가입 페이지 로드 시 자동 발급)
     *
     * Lambda 검증:
     * - Lambda는 role: GUEST 또는 userId == 0 체크로 회원가입 업로드 판별
     * - TTL 1시간 설정 (회원가입 안 하면 자동 삭제)
     */
    @GetMapping("/guest-token")
    public ResponseEntity<ApiResponse<String>> getGuestToken() {
        String guestToken = authService.generateGuestToken();
        return ResponseEntity.ok(ApiResponse.success("guest_token_issued", guestToken));
    }

    /**
     * Cookie 설정 헬퍼 메서드
     * @param name 쿠키 이름
     * @param value 쿠키 값
     * @param maxAge 만료 시간 (초)
     * @param path 쿠키 경로
     */
    private void setCookie(HttpServletResponse response, String name, String value, int maxAge, String path) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);  // 개발: false (HTTP), 운영: true (HTTPS)
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        // Cross-Origin 허용: Lax (개발 환경 localhost:3000 ↔ localhost:8080)
        // 운영 환경: Strict (같은 도메인 api.example.com ↔ www.example.com)
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /**
     * Cookie 추출 헬퍼 메서드
     * @param name 쿠키 이름
     * @return 쿠키 값 (없으면 null)
     */
    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
