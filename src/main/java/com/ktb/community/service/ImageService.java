package com.ktb.community.service;

import com.ktb.community.dto.request.ImageMetadataRequest;
import com.ktb.community.dto.response.ImageResponse;
import com.ktb.community.dto.response.PresignedUrlResponse;
import com.ktb.community.entity.Image;
import com.ktb.community.enums.ErrorCode;
import com.ktb.community.exception.BusinessException;
import com.ktb.community.repository.ImageRepository;
import com.ktb.community.util.FileValidator;
import com.ktb.community.util.S3KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 이미지 업로드 서비스
 * LLD.md Section 7.5 참조 (S3 직접 연동 방식)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ImageRepository imageRepository;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    /**
     * 이미지 업로드
     * - 파일 검증 (MIME type, Magic Number)
     * - S3 업로드
     * - DB 저장 (expires_at = 1시간 후)
     */
    @Transactional
    public ImageResponse uploadImage(MultipartFile file) {
        log.debug("[Image] 이미지 업로드 시작: filename={}, size={}, contentType={}", 
            file.getOriginalFilename(), file.getSize(), file.getContentType());
        
        // 1. 파일 검증
        FileValidator.validateImageFile(file);

        // 2. S3 업로드
        String s3Key = S3KeyGenerator.generateKey(file.getOriginalFilename());
        String imageUrl = uploadToS3(file, s3Key);

        // 3. DB 저장 (expires_at = 1시간 후)
        Image image = Image.builder()
                .imageUrl(imageUrl)
                .fileSize((int) file.getSize())
                .originalFilename(file.getOriginalFilename())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        Image savedImage = imageRepository.save(image);
        log.info("[Image] 이미지 업로드 완료: imageId={}, s3Key={}", savedImage.getImageId(), s3Key);

        return ImageResponse.from(savedImage);
    }

    /**
     * 이미지 메타데이터 등록 (Lambda 업로드 후 Backend 등록)
     * - imageUrl 검증 (S3 버킷 URL 형식)
     * - 중복 검증
     * - DB 저장 (expires_at = 1시간 후)
     *
     * 플로우: Lambda (S3 업로드) → Backend (메타데이터 등록) → imageId 반환
     */
    @Transactional
    public ImageResponse registerImageMetadata(ImageMetadataRequest request) {
        log.debug("[Image] 이미지 메타데이터 등록 시작: imageUrl={}, fileSize={}",
            request.getImageUrl(), request.getFileSize());

        // 1. imageUrl 형식 검증
        String s3BaseUrl = String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
        if (!request.getImageUrl().startsWith(s3BaseUrl)) {
            log.warn("[Image] 잘못된 이미지 URL 형식: imageUrl={}", request.getImageUrl());
            throw new BusinessException(ErrorCode.INVALID_IMAGE_URL,
                "Image URL must start with: " + s3BaseUrl);
        }

        // 2. 중복 검증
        if (imageRepository.existsByImageUrl(request.getImageUrl())) {
            log.warn("[Image] 이미 등록된 이미지 URL: imageUrl={}", request.getImageUrl());
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                "Image URL already exists: " + request.getImageUrl());
        }

        // 3. DB 저장 (expires_at = 1시간 후)
        Image image = Image.builder()
                .imageUrl(request.getImageUrl())
                .fileSize(request.getFileSize())
                .originalFilename(request.getOriginalFilename())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        Image savedImage = imageRepository.save(image);
        log.info("[Image] 이미지 메타데이터 등록 완료: imageId={}, imageUrl={}",
            savedImage.getImageId(), savedImage.getImageUrl());

        return ImageResponse.from(savedImage);
    }

    /**
     * S3 업로드 수행
     * - PutObjectRequest 생성 및 S3 업로드
     * - 이미지 URL 반환
     */
    private String uploadToS3(MultipartFile file, String s3Key) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .acl(ObjectCannedACL.PUBLIC_READ)  // 이미지 객체만 public 설정
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

            // S3 URL 생성
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, s3Key);

        } catch (IOException e) {
            log.error("[Image] S3 업로드 실패 (IOException): s3Key={}", s3Key, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, 
                "S3 upload failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("[Image] S3 업로드 에러: s3Key={}", s3Key, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, 
                "S3 upload error: " + e.getMessage());
        }
    }


    /**
     * Presigned URL 발급 (클라이언트 직접 S3 업로드용)
     * API.md Section 4.3 참조
     *
     * 플로우:
     * 1. 확장자 검증
     * 2. S3 Key 생성
     * 3. Presigned URL 생성 (15분 유효)
     * 4. DB에 Image 레코드 사전 등록 (expires_at = 1시간)
     * 5. PresignedUrlResponse 반환
     *
     * @param filename 원본 파일명 (확장자 필수)
     * @param contentType MIME type (선택, null이면 확장자 기반 추론)
     * @return Presigned URL 응답 (imageId, uploadUrl, s3Key, expiresAt)
     */
    @Transactional
    public PresignedUrlResponse generatePresignedUrl(String filename, String contentType) {
        log.debug("[Image] Presigned URL 발급 시작: filename={}, contentType={}", filename, contentType);

        // 1. 확장자 검증 (.jpg, .jpeg, .png, .gif)
        FileValidator.validateExtension(filename);

        // 2. Content-Type 추론 (전달되지 않은 경우)
        String resolvedContentType = (contentType != null && !contentType.isBlank())
                ? contentType
                : FileValidator.inferContentType(filename);

        // 3. S3 Key 생성 (images/yyyy/MM/dd/{UUID}.ext)
        String s3Key = S3KeyGenerator.generateKey(filename);

        // 4. Presigned URL 생성 (15분 유효)
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(resolvedContentType)
                .acl(ObjectCannedACL.PUBLIC_READ)  // 이미지 객체 public 설정 (다운로드 허용)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presignedRequest.url().toString();

        // 5. DB에 Image 레코드 사전 등록 (expires_at = 1시간 후)
        //    - 클라이언트가 업로드 완료 후 Post/User에 연결하면 clearExpiresAt() 호출
        //    - 미연결 시 ImageCleanupBatchService가 정리
        String imageUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, s3Key);
        Image image = Image.builder()
                .imageUrl(imageUrl)
                .fileSize(0)  // 클라이언트 직접 업로드이므로 파일 크기 미확인
                .originalFilename(filename)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        Image savedImage = imageRepository.save(image);
        log.info("[Image] Presigned URL 발급 완료: imageId={}, s3Key={}", savedImage.getImageId(), s3Key);

        return PresignedUrlResponse.of(
                savedImage.getImageId(),
                uploadUrl,
                s3Key,
                savedImage.getExpiresAt()
        );
    }
}
