package com.ktb.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 이미지 메타데이터 등록 요청 DTO
 * API.md Section 4.1 참조
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageMetadataRequest {

    @NotBlank(message = "이미지 URL은 필수입니다")
    private String imageUrl;

    /**
     * 선택: 파일 크기 (bytes)
     */
    @Positive(message = "파일 크기는 양수여야 합니다")
    private Integer fileSize;

    /**
     * 선택: 원본 파일명
     */
    private String originalFilename;
}
