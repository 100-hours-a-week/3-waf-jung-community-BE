package com.ktb.community.service;

import com.ktb.community.dto.response.PresignedUrlResponse;
import com.ktb.community.entity.Image;
import com.ktb.community.enums.ErrorCode;
import com.ktb.community.exception.BusinessException;
import com.ktb.community.repository.ImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ImageService 단위 테스트
 * API.md Section 4.3 Presigned URL 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImageService 테스트")
class ImageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private ImageRepository imageRepository;

    @InjectMocks
    private ImageService imageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(imageService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(imageService, "region", "ap-northeast-2");
    }

    @Nested
    @DisplayName("Presigned URL 발급 테스트")
    class GeneratePresignedUrlTest {

        @Test
        @DisplayName("성공 - 유효한 JPG 파일명으로 Presigned URL 발급")
        void generatePresignedUrl_Success_Jpg() throws Exception {
            // Given
            String filename = "test-image.jpg";
            String contentType = "image/jpeg";
            
            URL mockUrl = new URI("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/2025/12/01/uuid.jpg?X-Amz-Signature=test").toURL();
            PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(mockUrl);
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedRequest);

            Image savedImage = Image.builder()
                    .imageUrl("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/2025/12/01/uuid.jpg")
                    .fileSize(0)
                    .originalFilename(filename)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();
            ReflectionTestUtils.setField(savedImage, "imageId", 1L);
            when(imageRepository.save(any(Image.class))).thenReturn(savedImage);

            // When
            PresignedUrlResponse response = imageService.generatePresignedUrl(filename, contentType);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getImageId()).isEqualTo(1L);
            assertThat(response.getUploadUrl()).contains("X-Amz-Signature");
            assertThat(response.getS3Key()).startsWith("images/");
            assertThat(response.getExpiresAt()).isNotNull();

            verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
            verify(imageRepository).save(any(Image.class));
        }

        @Test
        @DisplayName("성공 - Content-Type 미전달 시 확장자 기반 추론")
        void generatePresignedUrl_Success_InferContentType() throws Exception {
            // Given
            String filename = "test-image.png";
            
            URL mockUrl = new URI("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uuid.png?X-Amz-Signature=test").toURL();
            PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(mockUrl);
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedRequest);

            Image savedImage = Image.builder()
                    .imageUrl("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uuid.png")
                    .fileSize(0)
                    .originalFilename(filename)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();
            ReflectionTestUtils.setField(savedImage, "imageId", 2L);
            when(imageRepository.save(any(Image.class))).thenReturn(savedImage);

            // When
            PresignedUrlResponse response = imageService.generatePresignedUrl(filename, null);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getImageId()).isEqualTo(2L);
            verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
        }

        @Test
        @DisplayName("성공 - GIF 파일 지원")
        void generatePresignedUrl_Success_Gif() throws Exception {
            // Given
            String filename = "animation.gif";
            
            URL mockUrl = new URI("https://test-bucket.s3.amazonaws.com/images/uuid.gif?sig=test").toURL();
            PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(mockUrl);
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedRequest);

            Image savedImage = Image.builder()
                    .imageUrl("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uuid.gif")
                    .fileSize(0)
                    .originalFilename(filename)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();
            ReflectionTestUtils.setField(savedImage, "imageId", 3L);
            when(imageRepository.save(any(Image.class))).thenReturn(savedImage);

            // When
            PresignedUrlResponse response = imageService.generatePresignedUrl(filename, null);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getS3Key()).endsWith(".gif");
        }

        @Test
        @DisplayName("실패 - 잘못된 확장자 (.exe)")
        void generatePresignedUrl_Fail_InvalidExtension_Exe() {
            // Given
            String filename = "malware.exe";

            // When & Then
            assertThatThrownBy(() -> imageService.generatePresignedUrl(filename, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE_TYPE);

            verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
            verify(imageRepository, never()).save(any(Image.class));
        }

        @Test
        @DisplayName("실패 - 잘못된 확장자 (.pdf)")
        void generatePresignedUrl_Fail_InvalidExtension_Pdf() {
            // Given
            String filename = "document.pdf";

            // When & Then
            assertThatThrownBy(() -> imageService.generatePresignedUrl(filename, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE_TYPE);
        }

        @Test
        @DisplayName("실패 - 확장자 없는 파일명")
        void generatePresignedUrl_Fail_NoExtension() {
            // Given
            String filename = "noextensionfile";

            // When & Then
            assertThatThrownBy(() -> imageService.generatePresignedUrl(filename, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE_TYPE);
        }

        @Test
        @DisplayName("실패 - 빈 파일명")
        void generatePresignedUrl_Fail_EmptyFilename() {
            // Given
            String filename = "";

            // When & Then
            assertThatThrownBy(() -> imageService.generatePresignedUrl(filename, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("실패 - null 파일명")
        void generatePresignedUrl_Fail_NullFilename() {
            // When & Then
            assertThatThrownBy(() -> imageService.generatePresignedUrl(null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        }
    }
}
