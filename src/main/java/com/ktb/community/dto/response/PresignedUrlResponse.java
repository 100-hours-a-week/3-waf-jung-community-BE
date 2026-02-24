package com.ktb.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Presigned URL 응답 DTO
 * API.md Section 4.3 참조
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {

    @JsonProperty("image_id")
    private Long imageId;

    @JsonProperty("upload_url")
    private String uploadUrl;

    @JsonProperty("s3_key")
    private String s3Key;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    public static PresignedUrlResponse of(Long imageId, String uploadUrl, String s3Key, LocalDateTime expiresAt) {
        return PresignedUrlResponse.builder()
                .imageId(imageId)
                .uploadUrl(uploadUrl)
                .s3Key(s3Key)
                .expiresAt(expiresAt)
                .build();
    }
}
