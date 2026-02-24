package com.ktb.community.repository;

import com.ktb.community.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이미지 Repository
 * FR-IMAGE-001, FR-IMAGE-003, FR-IMAGE-002 (고아 이미지)
 */
@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {

    /**
     * 만료된 이미지 조회 (고아 이미지 배치용)
     * - expires_at < 지정 시간
     * - idx_images_expires 인덱스 활용
     */
    List<Image> findByExpiresAtBefore(LocalDateTime dateTime);

    /**
     * 이미지 URL 존재 여부 확인 (Lambda 메타데이터 등록 시 중복 체크)
     */
    boolean existsByImageUrl(String imageUrl);

    /**
     * 영구 고아 이미지 조회 (7일 안전 마진)
     * - expires_at IS NULL + 아무 곳에도 연결되지 않은 이미지
     */
    @Query("SELECT i FROM Image i " +
           "WHERE i.expiresAt IS NULL " +
           "AND i.createdAt < :threshold " +
           "AND NOT EXISTS (SELECT u FROM User u WHERE u.profileImage = i) " +
           "AND NOT EXISTS (SELECT pi FROM PostImage pi WHERE pi.image = i)")
    List<Image> findPermanentOrphans(@Param("threshold") LocalDateTime threshold);
}
