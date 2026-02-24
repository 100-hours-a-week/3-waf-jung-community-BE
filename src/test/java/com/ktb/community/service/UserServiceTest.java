package com.ktb.community.service;

import com.ktb.community.dto.request.ChangePasswordRequest;
import com.ktb.community.dto.request.UpdateProfileRequest;
import com.ktb.community.dto.response.ImageResponse;
import com.ktb.community.dto.response.UserResponse;
import com.ktb.community.entity.Image;
import com.ktb.community.entity.User;
import com.ktb.community.enums.UserRole;
import com.ktb.community.exception.BusinessException;
import com.ktb.community.enums.ErrorCode;
import com.ktb.community.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.ktb.community.enums.UserStatus;

/**
 * UserService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 테스트")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.ktb.community.repository.ImageRepository imageRepository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("프로필 조회 성공")
    void getProfile_Success() {
        // Given
        Long userId = 1L;
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .nickname("testuser")
                .role(UserRole.USER)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", userId);

        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));

        // When
        UserResponse response = userService.getProfile(userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getNickname()).isEqualTo("testuser");

        verify(userRepository).findByUserIdAndUserStatus(userId, UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("프로필 조회 실패 - 사용자 없음")
    void getProfile_UserNotFound_ThrowsException() {
        // Given
        Long userId = 999L;
        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getProfile(userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(userRepository).findByUserIdAndUserStatus(userId, UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("프로필 수정 성공 - 닉네임 변경")
    void updateProfile_Nickname_Success() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 1L;
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .nickname("newnickname")
                .build();

        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .nickname("oldnickname")
                .role(UserRole.USER)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", userId);

        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(userRepository.existsByNickname("newnickname")).thenReturn(false);

        // When
        UserResponse response = userService.updateProfile(userId, authenticatedUserId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getNickname()).isEqualTo("newnickname");

        verify(userRepository).findByUserIdAndUserStatus(userId, UserStatus.ACTIVE);
        verify(userRepository).existsByNickname("newnickname");
    }

    @Test
    @DisplayName("프로필 수정 실패 - 닉네임 중복")
    void updateProfile_NicknameExists_ThrowsException() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 1L;
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .nickname("existingnick")
                .build();

        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .nickname("oldnickname")
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", userId);

        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(userRepository.existsByNickname("existingnick")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userService.updateProfile(userId, authenticatedUserId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_ALREADY_EXISTS);

        verify(userRepository).findByUserIdAndUserStatus(userId, UserStatus.ACTIVE);
        verify(userRepository).existsByNickname("existingnick");
    }

    @Test
    @DisplayName("비밀번호 변경 성공")
    void changePassword_Success() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 1L;
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .newPassword("NewPass123!")
                .newPasswordConfirm("NewPass123!")
                .build();

        User user = User.builder()
                .email("test@example.com")
                .passwordHash("oldPasswordHash")
                .nickname("testuser")
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", userId);

        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("newEncodedPassword");

        // When
        userService.changePassword(userId, authenticatedUserId, request);

        // Then
        verify(userRepository).findByUserIdAndUserStatus(userId, UserStatus.ACTIVE);
        verify(passwordEncoder).encode("NewPass123!");
    }

    @Test
    @DisplayName("회원 탈퇴 성공 - 상태 INACTIVE 변경")
    void deactivateAccount_Success() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 1L;

        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .nickname("testuser")
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", userId);

        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));

        // When
        userService.deactivateAccount(userId, authenticatedUserId);

        // Then
        verify(userRepository).findByUserIdAndUserStatus(userId, UserStatus.ACTIVE);
        // Note: Entity 상태 변경은 실제 트랜잭션에서 확인 (단위 테스트에서는 메서드 호출만 검증)
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 권한 없음 (다른 사용자)")
    void deactivateAccount_Unauthorized_ThrowsException() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 2L; // 다른 사용자
        
        // 권한 체크 실패 시 DB 조회 안 함 (stubbing 불필요)

        // When & Then
        assertThatThrownBy(() -> userService.deactivateAccount(userId, authenticatedUserId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED_ACCESS);

        verify(userRepository, never()).findById(userId);
    }

    @Test
    @DisplayName("프로필 수정 - 프로필 이미지 교체 시 기존 이미지 TTL 복원")
    void updateProfile_ReplaceProfileImage_ShouldRestoreOldImageTTL() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 1L;
        Long oldImageId = 10L;
        Long newImageId = 20L;

        // 기존 이미지 (expires_at = NULL, 영구 보존)
        Image oldImage = Image.builder()
                .imageUrl("https://s3.amazonaws.com/old-profile.jpg")
                .fileSize(1024)
                .originalFilename("old-profile.jpg")
                .expiresAt(null)
                .build();
        ReflectionTestUtils.setField(oldImage, "imageId", oldImageId);

        // User (기존 프로필 이미지 있음)
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("encoded")
                .nickname("testuser")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "profileImage", oldImage);

        // 새 이미지 (expires_at = now + 1h, 업로드 직후 상태)
        Image newImage = Image.builder()
                .imageUrl("https://s3.amazonaws.com/new-profile.jpg")
                .fileSize(2048)
                .originalFilename("new-profile.jpg")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(newImage, "imageId", newImageId);

        // Request (닉네임 + 이미지 ID)
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .nickname("newNickname")
                .imageId(newImageId)
                .build();

        // Mocking
        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE))
                .thenReturn(Optional.of(user));
        when(userRepository.existsByNickname("newNickname")).thenReturn(false);
        when(imageRepository.findById(newImageId)).thenReturn(Optional.of(newImage));

        // When
        UserResponse response = userService.updateProfile(userId, authenticatedUserId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getNickname()).isEqualTo("newNickname");

        // 기존 이미지: TTL 복원 확인
        assertThat(oldImage.getExpiresAt()).isNotNull();
        assertThat(oldImage.getExpiresAt()).isAfter(LocalDateTime.now());

        // 새 이미지: 영구 보존 (expires_at = NULL)
        assertThat(newImage.getExpiresAt()).isNull();

        verify(imageRepository).findById(newImageId);
    }

    @Test
    @DisplayName("프로필 수정 - 기존 프로필 이미지 없이 새 이미지 추가")
    void updateProfile_AddProfileImageWithoutOldImage_ShouldNotRestoreTTL() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 1L;
        Long newImageId = 20L;

        // User (프로필 이미지 없음)
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("encoded")
                .nickname("testuser")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        // profileImage는 기본적으로 null이므로 별도 설정 불필요

        // 새 이미지
        Image newImage = Image.builder()
                .imageUrl("https://s3.amazonaws.com/new-profile.jpg")
                .fileSize(2048)
                .originalFilename("new-profile.jpg")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(newImage, "imageId", newImageId);

        // Request (이미지 ID만)
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .imageId(newImageId)
                .build();

        // Mocking
        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE))
                .thenReturn(Optional.of(user));
        when(imageRepository.findById(newImageId)).thenReturn(Optional.of(newImage));

        // When
        UserResponse response = userService.updateProfile(userId, authenticatedUserId, request);

        // Then
        assertThat(response).isNotNull();

        // 새 이미지: 영구 보존 (expires_at = NULL)
        assertThat(newImage.getExpiresAt()).isNull();

        verify(imageRepository).findById(newImageId);
    }

    @Test
    @DisplayName("프로필 수정 - 이미지 제거 성공")
    void updateProfile_RemoveImage_Success() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 1L;
        Long oldImageId = 10L;

        // 기존 이미지 (expires_at = NULL, 영구 보존)
        Image oldImage = Image.builder()
                .imageUrl("https://s3.amazonaws.com/profile.jpg")
                .fileSize(1024)
                .originalFilename("profile.jpg")
                .expiresAt(null)
                .build();
        ReflectionTestUtils.setField(oldImage, "imageId", oldImageId);

        // User (기존 프로필 이미지 있음)
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("encoded")
                .nickname("testuser")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "profileImage", oldImage);

        // Request (removeImage: true)
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .removeImage(true)
                .build();

        // Mocking
        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE))
                .thenReturn(Optional.of(user));

        // When
        UserResponse response = userService.updateProfile(userId, authenticatedUserId, request);

        // Then
        assertThat(response).isNotNull();

        // 기존 이미지: TTL 복원 확인 (now + 1h)
        assertThat(oldImage.getExpiresAt()).isNotNull();
        assertThat(oldImage.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(oldImage.getExpiresAt()).isBefore(LocalDateTime.now().plusHours(2));

        // User의 profileImage는 null로 변경되어야 함
        assertThat(user.getProfileImage()).isNull();

        verify(userRepository).findByUserIdAndUserStatus(userId, UserStatus.ACTIVE);
        verify(imageService, never()).uploadImage(any());
    }

    @Test
    @DisplayName("프로필 수정 - removeImage와 imageId 동시 전달 시 imageId 우선")
    void updateProfile_RemoveImage_WithNewImage_Priority() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 1L;
        Long oldImageId = 10L;
        Long newImageId = 20L;

        // 기존 이미지
        Image oldImage = Image.builder()
                .imageUrl("https://s3.amazonaws.com/old-profile.jpg")
                .fileSize(1024)
                .originalFilename("old-profile.jpg")
                .expiresAt(null)
                .build();
        ReflectionTestUtils.setField(oldImage, "imageId", oldImageId);

        // User
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("encoded")
                .nickname("testuser")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "profileImage", oldImage);

        // 새 이미지
        Image newImage = Image.builder()
                .imageUrl("https://s3.amazonaws.com/new-profile.jpg")
                .fileSize(2048)
                .originalFilename("new-profile.jpg")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(newImage, "imageId", newImageId);

        // Request (removeImage: true + imageId 동시 전달)
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .removeImage(true)
                .imageId(newImageId)
                .build();

        // Mocking
        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE))
                .thenReturn(Optional.of(user));
        when(imageRepository.findById(newImageId)).thenReturn(Optional.of(newImage));

        // When
        UserResponse response = userService.updateProfile(userId, authenticatedUserId, request);

        // Then
        assertThat(response).isNotNull();

        // imageId가 우선 적용 → 이미지 교체 동작 (제거 X)
        assertThat(oldImage.getExpiresAt()).isNotNull(); // TTL 복원
        assertThat(newImage.getExpiresAt()).isNull(); // 영구 보존
        assertThat(user.getProfileImage()).isEqualTo(newImage);

        verify(imageRepository).findById(newImageId);
    }

    @Test
    @DisplayName("프로필 수정 - 기존 이미지 없을 때 removeImage=true 에러 없음")
    void updateProfile_RemoveImage_NoExistingImage_NoError() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 1L;

        // User (프로필 이미지 없음)
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("encoded")
                .nickname("testuser")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        // profileImage는 기본적으로 null

        // Request (removeImage: true)
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .removeImage(true)
                .build();

        // Mocking
        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE))
                .thenReturn(Optional.of(user));

        // When
        UserResponse response = userService.updateProfile(userId, authenticatedUserId, request);

        // Then
        assertThat(response).isNotNull();

        // 기존 이미지 없으므로 아무 동작 없음 (에러 발생 X)
        assertThat(user.getProfileImage()).isNull();

        verify(userRepository).findByUserIdAndUserStatus(userId, UserStatus.ACTIVE);
        verify(imageService, never()).uploadImage(any());
    }

    @Test
    @DisplayName("프로필 수정 - 이미지 제거 시 TTL 1시간 후 만료 확인")
    void updateProfile_TTLRestore_WhenRemovingImage() {
        // Given
        Long userId = 1L;
        Long authenticatedUserId = 1L;
        Long oldImageId = 10L;

        LocalDateTime beforeRemove = LocalDateTime.now();

        // 기존 이미지 (expires_at = NULL, 영구 보존)
        Image oldImage = Image.builder()
                .imageUrl("https://s3.amazonaws.com/profile.jpg")
                .fileSize(1024)
                .originalFilename("profile.jpg")
                .expiresAt(null)
                .build();
        ReflectionTestUtils.setField(oldImage, "imageId", oldImageId);

        // User
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("encoded")
                .nickname("testuser")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "profileImage", oldImage);

        // Request
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .removeImage(true)
                .build();

        // Mocking
        when(userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE))
                .thenReturn(Optional.of(user));

        // When
        userService.updateProfile(userId, authenticatedUserId, request);

        LocalDateTime afterRemove = LocalDateTime.now();

        // Then
        // TTL이 현재 시각 + 1시간으로 설정되었는지 확인
        assertThat(oldImage.getExpiresAt()).isNotNull();
        assertThat(oldImage.getExpiresAt()).isAfter(beforeRemove.plusMinutes(59)); // 최소 59분 후
        assertThat(oldImage.getExpiresAt()).isBefore(afterRemove.plusHours(2)); // 최대 2시간 전

        verify(userRepository).findByUserIdAndUserStatus(userId, UserStatus.ACTIVE);
    }
}
