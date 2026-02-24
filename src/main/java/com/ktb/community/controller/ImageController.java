package com.ktb.community.controller;

import com.ktb.community.config.RateLimit;
import com.ktb.community.dto.ApiResponse;
import com.ktb.community.dto.request.ImageMetadataRequest;
import com.ktb.community.dto.response.ImageResponse;
import com.ktb.community.dto.response.PresignedUrlResponse;
import com.ktb.community.service.ImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 업로드 컨트롤러
 * API.md Section 4 참조
 */
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * 이미지 업로드 (API.md Section 4.1)
     * POST /images
     * Authorization: Bearer {access_token}
     * Tier 2: 중간 제한 (파일 업로드 부하)
     *
     * @param file 업로드할 이미지 파일
     * @return 이미지 정보 (image_id, image_url)
     */
    @PostMapping
    @RateLimit(requestsPerMinute = 10)
    public ResponseEntity<ApiResponse<ImageResponse>> uploadImage(
            @RequestParam("file") MultipartFile file) {

        ImageResponse imageResponse = imageService.uploadImage(file);

        ApiResponse<ImageResponse> response = ApiResponse.success(
                "upload_image_success",
                imageResponse
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 이미지 메타데이터 등록 (API.md Section 4.1)
     * POST /images/metadata
     * Authorization: Bearer {access_token}
     * Tier 2: 중간 제한 (Lambda 이후 메타데이터 등록)
     */
    @PostMapping("/metadata")
    @RateLimit(requestsPerMinute = 10)
    public ResponseEntity<ApiResponse<ImageResponse>> registerImageMetadata(
            @RequestBody @Valid ImageMetadataRequest request) {

        ImageResponse imageResponse = imageService.registerImageMetadata(request);

        ApiResponse<ImageResponse> response = ApiResponse.success(
                "register_image_metadata_success",
                imageResponse
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    /**
     * Presigned URL 발급 (API.md Section 4.3)
     * GET /images/presigned-url
     * Authorization: Bearer {access_token | guest_token}
     * Tier 2: 중간 제한 (10회/분)
     *
     * 클라이언트가 S3에 직접 업로드할 수 있는 Presigned URL 발급
     * - 대용량 파일 업로드에 효율적 (서버 바이패스)
     * - 업로드 후 imageId로 Post/User에 연결
     *
     * @param filename 원본 파일명 (필수, 확장자 포함)
     * @param contentType MIME type (선택, 미전달 시 확장자 기반 추론)
     * @return Presigned URL 정보 (imageId, uploadUrl, s3Key, expiresAt)
     */
    @GetMapping("/presigned-url")
    @RateLimit(requestsPerMinute = 10)
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
            @RequestParam("filename") String filename,
            @RequestParam(value = "content_type", required = false) String contentType) {

        PresignedUrlResponse presignedUrlResponse = imageService.generatePresignedUrl(filename, contentType);

        ApiResponse<PresignedUrlResponse> response = ApiResponse.success(
                "presigned_url_generated",
                presignedUrlResponse
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
