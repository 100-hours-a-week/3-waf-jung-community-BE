---
name: lld-summary
description: 아키텍처 및 설계 패턴 요약. 구현 패턴 빠른 파악용.
---

# 설계 요약

## 기술 스택

- **Backend**: Spring Boot 3.5.6, Java 21, Gradle
- **Database**: MySQL 8.0+, JPA/Hibernate, HikariCP
- **Security**: Spring Security, JWT, BCrypt
- **Storage**: AWS S3 (이미지)
- **Package**: `com.ktb.community`

## 핵심 설계 결정

| 영역 | 결정사항 | 상세 Section |
|------|---------|-------------|
| 아키텍처 | ALB 경로 기반 라우팅 (/api/v1/* → BE) | Section 2 |
| 인증 | JWT (AT 15분, RT 7일), httpOnly Cookie | Section 6 |
| 이미지 | S3 직접 업로드 + Presigned URL (하이브리드), TTL 1시간 | Section 7.5 |
| 동시성 | 원자적 UPDATE (PostStats), clearAutomatically=false | Section 7.2, 12.3 |
| 페이지네이션 | 하이브리드 (latest=cursor, likes=offset) | Section 7.3 |
| Rate Limit | Token Bucket, 3-Tier 전략, Bucket4j | Section 6.5 |
| 예외 처리 | BusinessException + ErrorCode enum | Section 8 |

## 자주 참조되는 Section

| Section | 내용 |
|---------|------|
| **2** | ALB 라우팅, 3-Layer 책임 |
| **6** | JWT, 비밀번호 정책, Rate Limiting |
| **7** | 게시글/댓글/좋아요/이미지 로직 |
| **8** | 예외 처리 구조 |
| **12** | N+1 방지, 동시성 제어 |

## 5가지 이미지 업로드 패턴

| 패턴 | 사용처 | 트랜잭션 |
|------|--------|----------|
| Multipart 직접 | 회원가입, 프로필 수정 | 원자적 |
| 2단계 업로드 | 게시글 작성/수정 | 독립적 |
| 이미지 제거 | 프로필 수정 (removeImage) | 원자적 |
| **Presigned URL** | 클라이언트 직접 업로드 (대용량) | 독립적 |
| Lambda 메타데이터 | Lambda 이미지 처리 | 독립적 |

→ **상세 구현 패턴**: `docs/be/LLD.md` Section N 참조
