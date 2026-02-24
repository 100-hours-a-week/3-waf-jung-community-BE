package com.ktb.community.service;

import com.ktb.community.entity.Image;
import com.ktb.community.enums.ErrorCode;
import com.ktb.community.exception.BusinessException;
import com.ktb.community.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이미지 정리 배치 서비스
 * - 고아 이미지 자동 삭제 (expires_at < NOW())
 * - 영구 고아 이미지 자동 삭제 (expires_at IS NULL + 7일 안전 마진)
 * - 매일 새벽 3시 실행
 */
@Slf4j
@Service
public class ImageCleanupBatchService {

    private final ImageRepository imageRepository;
    private final S3Client s3Client;
    private final ImageCleanupBatchService self;  // Self-injection for proxy access

    @Value("${aws.s3.bucket}")
    private String bucketName;

    /**
     * Self-injection 생성자 (@Lazy로 순환 의존성 방지)
     */
    public ImageCleanupBatchService(
            ImageRepository imageRepository,
            S3Client s3Client,
            @Lazy ImageCleanupBatchService self
    ) {
        this.imageRepository = imageRepository;
        this.s3Client = s3Client;
        this.self = self;
    }

    /**
     * 고아 이미지 정리 배치 작업 (FR-IMAGE-002)
     * - 스케줄: 매일 새벽 3시 (CRON: 0 0 3 * * ?)
     * - TTL 만료 이미지 삭제 (S3 + DB Hard Delete)
     * - Self-injection으로 REQUIRES_NEW 트랜잭션 보장
     * 
     * 상세: LLD.md Section 7.5 참조
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOrphanImages() {
        log.info("[Batch] 고아 이미지 정리 배치 시작");
        long startTime = System.currentTimeMillis();

        LocalDateTime now = LocalDateTime.now();
        List<Image> expiredImages = imageRepository.findByExpiresAtBefore(now);

        if (expiredImages.isEmpty()) {
            log.info("[Batch] 삭제할 고아 이미지 없음");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (Image image : expiredImages) {
            try {
                // 프록시를 통해 호출하여 REQUIRES_NEW 트랜잭션 적용
                self.deleteImageInNewTransaction(image);
                successCount++;
                log.debug("[Batch] 이미지 삭제 성공: imageId={}", image.getImageId());

            } catch (Exception e) {
                failCount++;
                log.error("[Batch] 이미지 삭제 실패: imageId={}, error={}",
                         image.getImageId(), e.getMessage(), e);
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("[Batch] 고아 이미지 정리 완료: 성공={}, 실패={}, 전체={}, 소요시간={}ms",
                successCount, failCount, expiredImages.size(), elapsedTime);
    }

    /**
     * 영구 고아 이미지 정리 배치 작업 (Phase 5)
     * - 스케줄: 매일 새벽 4시 (CRON: 0 0 4 * * ?)
     * - 영구 보존(expires_at=NULL)이지만 아무 곳에도 연결되지 않은 이미지 삭제
     * - 7일 안전 마진: 생성 후 7일이 지난 이미지만 삭제
     *
     * 시나리오:
     * - 프로필 수정 중 새 이미지 업로드 후 저장 취소
     * - 게시글 작성 중 이미지 업로드 후 작성 취소
     * - clearExpiresAt() 호출 후 실제 연결이 완료되지 않은 경우
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanupPermanentOrphanImages() {
        log.info("[Batch] 영구 고아 이미지 정리 배치 시작");
        long startTime = System.currentTimeMillis();

        // 7일 전 기준 (안전 마진)
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<Image> permanentOrphans = imageRepository.findPermanentOrphans(threshold);

        if (permanentOrphans.isEmpty()) {
            log.info("[Batch] 삭제할 영구 고아 이미지 없음");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (Image image : permanentOrphans) {
            try {
                // 프록시를 통해 호출하여 REQUIRES_NEW 트랜잭션 적용
                self.deleteImageInNewTransaction(image);
                successCount++;
                log.debug("[Batch] 영구 고아 이미지 삭제 성공: imageId={}", image.getImageId());

            } catch (Exception e) {
                failCount++;
                log.error("[Batch] 영구 고아 이미지 삭제 실패: imageId={}, error={}",
                         image.getImageId(), e.getMessage(), e);
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("[Batch] 영구 고아 이미지 정리 완료: 성공={}, 실패={}, 전체={}, 소요시간={}ms",
                successCount, failCount, permanentOrphans.size(), elapsedTime);
    }

    /**
     * 독립적인 트랜잭션으로 이미지 삭제 (REQUIRES_NEW)
     * - 실패 시 해당 트랜잭션만 롤백
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteImageInNewTransaction(Image image) {
        // 1. S3 파일 삭제
        String s3Key = extractS3Key(image.getImageUrl());
        deleteFromS3(s3Key);

        // 2. DB 레코드 삭제 (Hard Delete)
        imageRepository.delete(image);
    }

    /**
     * S3 파일 삭제
     */
    private void deleteFromS3(String s3Key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);

        } catch (Exception e) {
            log.error("[Batch] S3 파일 삭제 실패: s3Key={}, error={}", s3Key, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * S3 URL에서 키 추출
     * 예시: https://bucket-name.s3.region.amazonaws.com/images/2025/10/11/test.jpg
     *       → images/2025/10/11/test.jpg
     */
    private String extractS3Key(String imageUrl) {
        int keyStartIndex = imageUrl.indexOf(".com/") + 5;
        return imageUrl.substring(keyStartIndex);
    }
}
