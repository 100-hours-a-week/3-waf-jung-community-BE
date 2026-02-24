package com.ktb.community.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Value("${aws.s3.region}")
    private String region;

    /**
     * S3Client Bean 생성
     *
     * Credentials Provider: DefaultCredentialsProvider (IAM Role)
     * - 로컬 개발: ~/.aws/credentials 프로필 자동 인식
     * - EC2 배포: IAM Role 자동 인식
     * - 자동 갱신: AWS SDK에서 자동 처리
     * - AWS 공식 권장 방식
     *
     * 인증 순서:
     * 1. 환경변수 (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
     * 2. 시스템 프로퍼티
     * 3. ~/.aws/credentials 파일
     * 4. EC2 IAM Instance Profile
     * 5. ECS Task Role
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create())
                .build();
    }


    /**
     * S3Presigner Bean 생성 (Presigned URL 발급용)
     *
     * Presigned URL 특성:
     * - 임시 URL로 클라이언트가 S3에 직접 업로드 가능
     * - 서버를 거치지 않아 대용량 파일 업로드에 효율적
     * - URL 유효기간(15분) 내에만 업로드 가능
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create())
                .build();
    }

}
