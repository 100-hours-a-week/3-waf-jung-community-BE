package com.ktb.community.dto.request;

import com.ktb.community.entity.User;
import com.ktb.community.enums.UserRole;
import com.ktb.community.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원가입 요청 DTO
 * API.md Section 2.1 참조
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "유효한 이메일 형식이어야 합니다")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, max = 20, message = "비밀번호는 8-20자여야 합니다")
    private String password;

    @NotBlank(message = "닉네임은 필수입니다")
    @Size(max = 10, message = "닉네임은 최대 10자입니다")
    private String nickname;

    /**
     * 선택: 프로필 이미지 ID (POST /images로 먼저 업로드 필요)
     */
    private Long imageId;

    /**
     * DTO → Entity 변환
     * @param encodedPassword BCrypt 암호화된 비밀번호
     */
    public User toEntity(String encodedPassword) {
        return User.builder()
                .email(email.toLowerCase().trim())
                .passwordHash(encodedPassword)
                .nickname(nickname)
                .role(UserRole.USER)
                .build();
    }
}
