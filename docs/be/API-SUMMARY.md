---
name: api-summary
description: REST API 엔드포인트 요약. 엔드포인트/Method 빠른 파악용.
---

# API 요약

## ALB 라우팅

```
Client: /api/v1/auth/login → ALB strip → BE: /auth/login
```

- Production: `/api/v1/*` prefix 필요
- Local: `http://localhost:8080/*` 직접 호출

## 엔드포인트 맵

| Section | 도메인 | 엔드포인트 | Method |
|---------|--------|-----------|--------|
| 1 | 인증 | /auth/login | POST |
| 1 | 인증 | /auth/logout | POST |
| 1 | 인증 | /auth/refresh_token | POST |
| 1 | 인증 | /auth/guest-token | GET |
| 2 | 사용자 | /users/signup | POST |
| 2 | 사용자 | /users/{id} | GET, PATCH, PUT |
| 2 | 사용자 | /users/{id}/password | PATCH |
| 3 | 게시글 | /posts | GET, POST |
| 3 | 게시글 | /posts/{id} | GET, PATCH, DELETE |
| 4 | 이미지 | /images | POST |
| 4 | 이미지 | /images/presigned-url | GET |
| 4 | 이미지 | /images/metadata | POST |
| 5 | 댓글 | /posts/{id}/comments | GET, POST |
| 5 | 댓글 | /posts/{id}/comments/{cid} | PATCH, DELETE |
| 6 | 좋아요 | /posts/{id}/like | POST, DELETE |
| 8 | 시스템 | /health | GET |
| 8 | 시스템 | /stats | GET |

## 인증 방식

| 토큰 | 전달 | 저장      | 유효기간 |
|------|------|---------|----------|
| Access Token | 응답 body → Authorization header | 로컬 스토리지 | 15분 |
| Refresh Token | httpOnly Cookie | 브라우저 자동 | 7일 |
| Guest Token | 응답 body | 임시      | 5분 |

## 페이지네이션

| 정렬 | 방식 | 파라미터 | 응답 |
|------|------|----------|------|
| latest | Cursor | cursor, limit | nextCursor, hasMore |
| likes | Offset | offset, limit | total_count |

## 에러 코드 그룹

- **AUTH**: AUTH-001~004 (인증 실패, 토큰 오류)
- **USER**: USER-001~007 (사용자 오류, 비밀번호 정책)
- **POST**: POST-001~004 (게시글 오류)
- **COMMENT**: COMMENT-001~003 (댓글 오류)
- **LIKE**: LIKE-001~002 (좋아요 오류)
- **IMAGE**: IMAGE-001~003 (이미지 오류)
- **COMMON**: COMMON-001~004, 999 (공통 오류)

→ **상세 스펙**: `docs/be/API.md` Section N 참조
