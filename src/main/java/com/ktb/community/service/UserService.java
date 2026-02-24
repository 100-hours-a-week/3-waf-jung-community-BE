package com.ktb.community.service;

import com.ktb.community.dto.request.ChangePasswordRequest;
import com.ktb.community.dto.request.UpdateProfileRequest;
import com.ktb.community.dto.response.UserResponse;
import com.ktb.community.entity.Image;
import com.ktb.community.entity.User;
import com.ktb.community.enums.UserStatus;
import com.ktb.community.exception.BusinessException;
import com.ktb.community.enums.ErrorCode;
import com.ktb.community.repository.ImageRepository;
import com.ktb.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 서비스
 * PRD.md FR-USER-001~004 참조
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * 사용자 프로필 조회 (FR-USER-001)
     */
    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, 
                        "User not found or inactive with id: " + userId));
        
        return UserResponse.from(user);
    }
    
    /**
     * 사용자 프로필 수정 (FR-USER-002)
     * - 본인만 수정 가능
     * - 닉네임 중복 확인
     * - 프로필 이미지 참조 (2단계 업로드)
     */
    @Transactional
    public UserResponse updateProfile(Long userId, Long authenticatedUserId,
                                     UpdateProfileRequest request) {
        // 권한 확인
        if (!userId.equals(authenticatedUserId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        User user = userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "User not found or inactive with id: " + userId));

        // 닉네임 변경 시 중복 확인
        if (request.getNickname() != null && !request.getNickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.getNickname())) {
                throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS,
                        "Nickname already exists: " + request.getNickname());
            }
            user.updateNickname(request.getNickname());
        }

        // ========== 프로필 이미지 처리 ==========

        // Case 1: 새 이미지로 교체 (imageId: Long) - 최우선
        if (request.getImageId() != null) {
            // 1. 기존 이미지 TTL 복원 (고아 이미지 처리)
            Image oldImage = user.getProfileImage();
            if (oldImage != null) {
                oldImage.setExpiresAt(LocalDateTime.now().plusHours(1));
                log.info("[User] 고아 이미지 TTL 복원: imageId={}, expiresAt={}",
                         oldImage.getImageId(), oldImage.getExpiresAt());
            }

            // 2. 새 이미지 참조
            Image newImage = imageRepository.findById(request.getImageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND,
                            "Invalid or expired image ID: " + request.getImageId()));

            // 3. 새 이미지 연결 (TTL 해제 → 영구 보존)
            newImage.clearExpiresAt();
            user.updateProfileImage(newImage);

            log.info("[User] 프로필 이미지 변경: imageId={}", newImage.getImageId());
        }
        // Case 2: 이미지 제거 요청 (removeImage: true)
        else if (Boolean.TRUE.equals(request.getRemoveImage())) {
            Image oldImage = user.getProfileImage();
            if (oldImage != null) {
                // TTL 복원 (고아 이미지 처리)
                oldImage.setExpiresAt(LocalDateTime.now().plusHours(1));
                log.info("[User] 고아 이미지 TTL 복원: imageId={}, expiresAt={}",
                         oldImage.getImageId(), oldImage.getExpiresAt());

                // 관계 해제
                user.updateProfileImage(null);
                log.info("[User] 프로필 이미지 제거: userId={}", userId);
            }
        }
        // Case 3: 이미지 유지 (둘 다 없음)

        return UserResponse.from(user);
    }
    
    /**
     * 비밀번호 변경 (FR-USER-003)
     * - 본인만 변경 가능
     * - 비밀번호 정책 검증
     * - 비밀번호 확인 일치 검증
     */
    @Transactional
    public void changePassword(Long userId, Long authenticatedUserId, ChangePasswordRequest request) {
        // 권한 확인
        if (!userId.equals(authenticatedUserId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        User user = userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, 
                        "User not found or inactive with id: " + userId));
        
        // 비밀번호 암호화 및 업데이트
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.updatePassword(encodedPassword);
    }
    
    /**
     * 회원 탈퇴 (FR-USER-004)
     * - 본인만 탈퇴 가능
     * - Soft Delete (상태 변경)
     */
    @Transactional
    public void deactivateAccount(Long userId, Long authenticatedUserId) {
        // 권한 확인
        if (!userId.equals(authenticatedUserId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        User user = userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, 
                        "User not found or inactive with id: " + userId));
        
        // 상태 변경 (Soft Delete)
        user.updateStatus(UserStatus.INACTIVE);
    }

    /**
     * 이메일로 사용자 ID 조회 (Controller 인증용)
     * ACTIVE + INACTIVE 허용 (탈퇴 후 자기 글 삭제 = GDPR)
     */
    @Transactional(readOnly = true)
    public Long findUserIdByEmail(String email) {
        return userRepository.findByEmailAndUserStatusIn(
                email.toLowerCase().trim(),
                List.of(UserStatus.ACTIVE, UserStatus.INACTIVE))
                .map(User::getUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "User not found or deleted with email: " + email));
    }
    
}
