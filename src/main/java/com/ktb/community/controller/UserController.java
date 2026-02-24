package com.ktb.community.controller;

import com.ktb.community.config.RateLimit;
import com.ktb.community.dto.ApiResponse;
import com.ktb.community.dto.request.ChangePasswordRequest;
import com.ktb.community.dto.request.SignupRequest;
import com.ktb.community.dto.request.UpdateProfileRequest;
import com.ktb.community.dto.response.AuthResponse;
import com.ktb.community.dto.response.UserResponse;
import com.ktb.community.enums.ErrorCode;
import com.ktb.community.exception.BusinessException;
import com.ktb.community.service.AuthService;
import com.ktb.community.service.UserService;
import com.ktb.community.util.PasswordValidator;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// [세션 전환] JWT 방식 (미사용)
// import org.springframework.security.core.Authentication;
// import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 컨트롤러
 * API.md Section 2 참조
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    
    private final AuthService authService;
    private final UserService userService;
    
    /**
     * 회원가입 (API.md Section 2.1)
     * POST /users/signup or POST /users
     * JSON 방식: 2단계 업로드 (POST /images → POST /users/signup)
     * httpOnly Cookie 방식으로 토큰 전달 (자동 로그인)
     * Tier 2: 중간 제한 (spam bot 방지, 정상 사용자 재시도 고려)
     */
    @PostMapping(value = {"/signup", ""})
    @RateLimit(requestsPerMinute = 10)
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletResponse response) {

        // 비밀번호 정책 검증 (Bean Validation 외 추가 정책)
        if (!PasswordValidator.isValid(request.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_POLICY,
                    PasswordValidator.getPolicyDescription());
        }

        AuthService.AuthResult result = authService.signup(request);

        // [세션 방식] (보존)
        // setCookie(response, "SESSIONID", result.sessionId(), 3600, "/");

        // [JWT 방식] RT → httpOnly Cookie (7일, Path=/auth)
        setCookie(response, "refresh_token", result.refreshToken(),
                  7 * 24 * 3600, "/auth");

        // AT + 사용자 정보 → 응답 body
        AuthResponse authResponse = AuthResponse.from(result.user(), result.accessToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("register_success", authResponse));
    }
    
    /**
     * 사용자 정보 조회 (API.md Section 2.2)
     * GET /users/{userID}
     * Tier 3: 제한 없음 (조회 API)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(@PathVariable Long userId) {
        UserResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("get_profile_success", response));
    }
    
    /**
     * 사용자 정보 수정 (API.md Section 2.3)
     * PATCH /users/{userID}
     * JSON 방식: 2단계 업로드 (POST /images → PATCH /users/{userId})
     * Tier 2: 중간 제한 (프로필 수정 spam 방지)
     */
    @PatchMapping("/{userId}")
    @RateLimit(requestsPerMinute = 30)
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        
        Long authenticatedUserId = (Long) httpRequest.getAttribute("userId");
        UserResponse response = userService.updateProfile(userId, authenticatedUserId, request);
        
        return ResponseEntity.ok(ApiResponse.success("update_profile_success", response));
    }
    
    /**
     * 비밀번호 변경 (API.md Section 2.4)
     * PATCH /users/{userID}/password
     * Tier 1: 강한 제한 (enumeration 방지)
     */
    @PatchMapping("/{userId}/password")
    @RateLimit(requestsPerMinute = 5)
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable Long userId,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        
        // 1. 비밀번호 일치 검증
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH,
                    "Password confirmation does not match");
        }
        
        // 2. 비밀번호 정책 검증
        if (!PasswordValidator.isValid(request.getNewPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_POLICY,
                    PasswordValidator.getPolicyDescription());
        }
        
        Long authenticatedUserId = (Long) httpRequest.getAttribute("userId");
        userService.changePassword(userId, authenticatedUserId, request);
        
        return ResponseEntity.ok(ApiResponse.success("update_password_success"));
    }
    
    /**
     * 회원 탈퇴 (API.md Section 2.5)
     * PUT /users/{userID}
     * Tier 2: 중간 제한 (계정 비활성화 남용 방지)
     */
    @PutMapping("/{userId}")
    @RateLimit(requestsPerMinute = 10)
    public ResponseEntity<ApiResponse<Void>> deactivateAccount(
            @PathVariable Long userId,
            HttpServletRequest httpRequest) {
        
        Long authenticatedUserId = (Long) httpRequest.getAttribute("userId");
        userService.deactivateAccount(userId, authenticatedUserId);
        
        return ResponseEntity.ok(ApiResponse.success("account_deactivated_success"));
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

    // [세션 전환] JWT 방식 extractUserIdFromAuthentication (미사용)
    // /**
    //  * Authentication에서 사용자 ID 추출
    //  * JWT 인증: username = userId (숫자)
    //  * 기타 인증: username = email (fallback to DB lookup)
    //  */
    // private Long extractUserIdFromAuthentication(Authentication authentication) { ... }
}
