package com.ktb.community.service;

import com.ktb.community.dto.request.LoginRequest;
import com.ktb.community.dto.request.SignupRequest;
import com.ktb.community.entity.User;
import com.ktb.community.entity.UserToken;
import com.ktb.community.enums.UserStatus;
import com.ktb.community.exception.BusinessException;
import com.ktb.community.enums.ErrorCode;
import com.ktb.community.repository.ImageRepository;
import com.ktb.community.repository.UserRepository;
import com.ktb.community.repository.UserTokenRepository;
import com.ktb.community.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 인증 서비스
 * LLD.md Section 7.1, PRD.md FR-AUTH-001~004 참조
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 인증 결과 (Access Token + Refresh Token + 사용자 정보)
     * Service → Controller 전달용
     */
    public record AuthResult(String accessToken, String refreshToken, User user) {}

    /**
     * 토큰 쌍 (내부용)
     */
    private record TokenPair(String accessToken, String refreshToken) {}

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserTokenRepository userTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ImageService imageService;
    private final ImageRepository imageRepository;

    /**
     * 회원가입 (FR-AUTH-001)
     * - 이메일/닉네임 중복 확인
     * - 비밀번호 정책 검증
     * - 프로필 이미지 참조 (2단계 업로드)
     * - 자동 로그인 (토큰 발급)
     */
    @Transactional
    public AuthResult signup(SignupRequest request) {
        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Email already exists: " + request.getEmail());
        }

        // 닉네임 중복 확인
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS,
                    "Nickname already exists: " + request.getNickname());
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 프로필 이미지 참조 (있을 경우)
        com.ktb.community.entity.Image image = null;
        if (request.getImageId() != null) {
            image = imageRepository.findById(request.getImageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND,
                            "Invalid or expired image ID: " + request.getImageId()));
            image.clearExpiresAt();  // TTL 해제 → 영구 보존
            log.debug("[Auth] 회원가입 프로필 이미지 연결: imageId={}", image.getImageId());
        }

        // 사용자 생성
        User user = request.toEntity(encodedPassword);
        if (image != null) {
            user.updateProfileImage(image);
        }
        User savedUser = userRepository.save(user);

        // [JWT 방식]
        TokenPair tokens = generateTokens(savedUser);
        return new AuthResult(tokens.accessToken(), tokens.refreshToken(), savedUser);
    }

    /**
     * 로그인 (FR-AUTH-002)
     * - 이메일/비밀번호 검증
     * - 계정 상태 확인 (ACTIVE만 허용)
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmailWithProfileImage(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 계정 상태 확인
        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        // [JWT 방식]
        TokenPair tokens = generateTokens(user);
        return new AuthResult(tokens.accessToken(), tokens.refreshToken(), user);
    }

    /**
     * 로그아웃 (FR-AUTH-003)
     * - Refresh Token 삭제
     */
    @Transactional
    public void logout(String refreshToken) {
        userTokenRepository.deleteByToken(refreshToken);
        log.info("[Auth] 로그아웃 완료");
    }

    /**
     * Access Token 재발급 (FR-AUTH-004)
     * - Refresh Token 유효성 검증
     * - 새 Access Token 발급
     * - 사용자 정보 반환 (프론트엔드 동기화용)
     */
    @Transactional(readOnly = true)
    public AuthResult refreshAccessToken(String refreshToken) {
        // RT 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN,
                    "Invalid or expired refresh token");
        }

        // RT가 RDB에 존재하는지 확인
        UserToken userToken = userTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN,
                        "Refresh token not found in database"));

        // 사용자 조회
        User user = userRepository.findByUserIdAndUserStatus(
                        userToken.getUser().getUserId(), UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "User not found or inactive"));

        // 새 AT 생성 (RT는 재사용)
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getEmail(),
                user.getRole().name()
        );

        log.debug("[Auth] Access Token 재발급: userId={}", user.getUserId());
        return new AuthResult(accessToken, refreshToken, user);
    }

    /**
     * 토큰 생성 및 저장 (내부 메서드)
     */
    private TokenPair generateTokens(User user) {
        // AT/RT 생성
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getEmail(),
                user.getRole().name()
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        // Refresh Token RDB 저장
        UserToken userToken = UserToken.builder()
                .token(refreshToken)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        userTokenRepository.save(userToken);

        log.debug("[Auth] JWT 토큰 생성: userId={}", user.getUserId());
        return new TokenPair(accessToken, refreshToken);
    }

    /**
     * Guest Token 발급 (회원가입 이미지 업로드용, 5분)
     */
    public String generateGuestToken() {
        String guestToken = jwtTokenProvider.generateGuestToken();
        log.debug("[Auth] Guest Token 생성: role=GUEST, validity=5분");
        return guestToken;
    }
}
