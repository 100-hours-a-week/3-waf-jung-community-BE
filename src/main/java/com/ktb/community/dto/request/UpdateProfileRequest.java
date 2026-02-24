package com.ktb.community.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사용자 프로필 수정 요청 DTO
 * API.md Section 2.3 참조
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 10, message = "닉네임은 최대 10자입니다")
    private String nickname;

    /**
     * 선택: 새 프로필 이미지 ID (POST /images로 먼저 업로드 필요)
     */
    private Long imageId;

    /**
     * 선택: 프로필 이미지 제거 플래그
     * - true: 기존 이미지 제거 (TTL 복원 + 관계 해제)
     * - false/null: 기존 이미지 유지
     *
     * 주의: removeImage와 imageId 동시 전달 시 imageId가 우선 적용됨
     */
    private Boolean removeImage;
}
