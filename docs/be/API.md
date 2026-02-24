---
name: rest-api-spec
description: REST API 엔드포인트 명세서. API URL, HTTP Method, 요청/응답 형식, 에러 코드 확인 시 참조. FE 연동, Postman 테스트, ALB 라우팅 이해에 활용.
---

# API 명세서

**Version**: 1.0  
**Last Updated**: 2025-12-01  
**Status**: 완료 (Presigned URL 포함)

---

# DC2 커뮤니티 REST API 문서

## 목차
- [0. API 아키텍처](#0-api-아키텍처)
- [1. 인증 (Authentication)](#1-인증-authentication)
- [2. 사용자 (Users)](#2-사용자-users)
- [3. 게시글 (Posts)](#3-게시글-posts)
- [4. 이미지 (Images)](#4-이미지-images)
- [5. 댓글 (Comments)](#5-댓글-comments)
- [6. 좋아요 (Likes)](#6-좋아요-likes)
- [7. 공통 사양](#7-공통-사양)
- [8. 시스템 (System)](#8-시스템-system)

---

## 0. API 아키텍처

### ALB 경로 기반 라우팅

```
                         ┌─────────────────────────────────┐
                         │              ALB                │
   Client Request        │   Path-based Routing            │
        │                │                                 │
        ▼                │   /api/v1/* → BE (path rewrite) │
   ┌──────────┐          │   /*        → FE                │
   │/api/v1/  │─────────►│                                 │
   │auth/login│          │         ┌─────────┴─────────┐   │
   └──────────┘          │         ▼                   ▼   │
                         │   ┌──────────┐       ┌──────────┐
                         │   │ BE(8080) │       │ FE(3000) │
                         │   │ /auth/   │       │ /        │
                         │   │ login    │       │          │
                         │   └──────────┘       └──────────┘
                         └─────────────────────────────────┘
```

### API Base URL

| 환경 | 클라이언트 호출 | ALB 변환 | BE 수신 |
|------|----------------|----------|---------|
| **Production** | `/api/v1/auth/login` | strip `/api/v1` | `/auth/login` |
| **Local (직접)** | `http://localhost:8080/auth/login` | - | `/auth/login` |

### 클라이언트 호출 규칙

```javascript
// Production (ALB 경유)
const API_PREFIX = '/api/v1';
fetch(`${API_PREFIX}/auth/login`, { ... });  // → /api/v1/auth/login

// Local Development (BE 직접 호출)
const API_BASE_URL = 'http://localhost:8080';
fetch(`${API_BASE_URL}/auth/login`, { ... });  // → /auth/login
```

**⚠️ 중요**: 이 문서의 Endpoint는 **BE 내부 경로**입니다.
- Production 환경에서는 `/api/v1` prefix를 추가하여 호출
- ALB가 자동으로 prefix를 strip하여 BE로 전달

---

## 1. 인증 (Authentication)

### 1.1 로그인
**Endpoint:** `POST /auth/login`

**Request:** `{ "email": "test@startupcode.kr", "password": "test1234" }`

**필수:** email(String), password(String)

**응답:**
- 200: `login_success` → **AT는 응답 body, RT는 httpOnly Cookie**
    - Cookie: refresh_token (7일, HttpOnly, SameSite=Lax, Path=/auth)
    - Body: `{ userId, email, nickname, profileImage, accessToken }` (AuthResponse)
- 401: AUTH-001 (Invalid credentials), USER-005 (Account inactive)
- 400/500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "login_success",
  "data": {
    "userId": 1,
    "email": "test@startupcode.kr",
    "nickname": "testuser",
    "profileImage": "https://...",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "timestamp": "2025-10-21T10:00:00"
}
```

**토큰 사용:**
- **AT**: 응답 body의 `accessToken` 필드 → 클라이언트 JS 메모리 저장 → `Authorization: Bearer {token}` 헤더로 전송
- **RT**: httpOnly Cookie 자동 저장 → 브라우저가 `/auth/refresh_token` 요청 시 자동 전송
- **AT 유효기간**: 15분
- **RT 유효기간**: 7일

---

### 1.2 로그아웃
**Endpoint:** `POST /auth/logout`

**Request:** 없음 (Cookie에서 자동 추출)

**처리:**
- Cookie에서 refresh_token 추출 → DB 삭제
- RT 쿠키 삭제 (MaxAge=0)

**응답:**
- 200: `logout_success`
- 400/500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "logout_success",
  "data": null,
  "timestamp": "2025-10-21T10:00:00"
}
```

**참고:** AT는 클라이언트에서 localStorage에서 삭제 (`localStorage.removeItem('accessToken')`)

---

### 1.3 액세스 토큰 재발급
**Endpoint:** `POST /auth/refresh_token`

**Request:** 없음 (Cookie에서 자동 추출)

**처리:**
- Cookie에서 refresh_token 추출 → 검증
- 새 access_token 발급 → 응답 body로 전달
- 사용자 정보 반환 (localStorage 동기화용)

**응답:**
- 200: `token_refreshed` → **새 AT는 응답 body, RT는 재사용**
    - Body: `{ userId, email, nickname, profileImage, accessToken }` (AuthResponse)
- 401: AUTH-004 (Invalid refresh token)
- 400/500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "token_refreshed",
  "data": {
    "userId": 1,
    "email": "test@startupcode.kr",
    "nickname": "testuser",
    "profileImage": "https://...",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "timestamp": "2025-10-21T10:00:00"
}
```

**참고:** RT는 재사용되며, 쿠키는 업데이트되지 않음 (7일 후 재로그인 필요)

**프론트엔드 활용:**
```javascript
// api.js - 토큰 갱신 시 localStorage 동기화
async function refreshAccessToken() {
    const response = await fetch(`${API_BASE_URL}/auth/refresh_token`, {
        method: 'POST',
        credentials: 'include'
    });

    if (response.ok) {
        const data = await response.json();
        // userId를 localStorage에 저장 (옵션 3 지원)
        if (data.data && data.data.userId) {
            localStorage.setItem('userId', data.data.userId);
        }
        return true;
    }
    return false;
}
```

---

### 1.4 Guest Token 발급
**Endpoint:** `GET /auth/guest-token`

**용도:** 회원가입 시 프로필 이미지 업로드를 위한 임시 토큰

**응답:**
- 200: `guest_token_issued` → Guest Token (String)
- 500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "guest_token_issued",
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "timestamp": "2025-10-21T10:00:00"
}
```

**토큰 특성:**

| 항목 | 값 |  
|------|-----|  
| 유효기간 | 5분 |  
| subject | "0" (게스트 전용 ID) |  
| role | GUEST |  
| Refresh Token | 없음 (일회용) |  

**사용 시나리오:**
1. 회원가입 페이지 로드 시 자동 발급
2. 프로필 이미지 업로드 (Presigned URL 발급 → S3 직접 업로드)
3. 회원가입 완료 후 정식 AT/RT로 교체

**Presigned URL 검증:**
- BE는 `role: GUEST` 또는 `userId == 0` 체크로 회원가입 업로드 판별
- 미사용 이미지는 TTL 1시간 후 자동 삭제 (배치 작업)

---

## 2. 사용자 (Users)

### 2.1 회원가입
**Endpoint:** `POST /users/signup`

**Content-Type:** `multipart/form-data`

**Request Parts:**
- `email` (String, 필수) - 이메일 주소
- `password` (String, 필수) - 비밀번호 (8-20자, 대/소/특수문자 각 1개+)
- `nickname` (String, 필수) - 닉네임 (10자 이내)
- `profileImage` (File, 선택) - 프로필 이미지 (JPG/PNG/GIF, 최대 5MB)

**응답:**
- 201: `register_success` → **AT는 응답 body, RT는 httpOnly Cookie** (자동 로그인)
    - Cookie: refresh_token (7일, HttpOnly, SameSite=Lax, Path=/auth)
    - Body: `{ userId, email, nickname, profileImage, accessToken }` (AuthResponse)
- 409: USER-002 (Email exists), USER-003 (Nickname exists)
- 400: USER-004 (Password policy)
- 413: IMAGE-002 (File too large)
- 400: IMAGE-003 (Invalid file type)
- 400/500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "register_success",
  "data": {
    "userId": 1,
    "email": "test@startupcode.kr",
    "nickname": "testuser",
    "profileImage": "https://...",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "timestamp": "2025-10-21T10:00:00"
}
```

**참고:** 로그인과 동일한 토큰 사용 방식 (AT 15분, RT 7일)

---

### 2.2 사용자 정보 조회
**Endpoint:** `GET /users/{userID}`

**응답:**
- 200: `get_profile_success` → image, nickname, email 반환
- 404: USER-001 (User not found)
- 401/500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "get_profile_success",
  "data": {
    "userId": 1,
    "email": "test@startupcode.kr",
    "nickname": "testuser",
    "profileImage": "https://ktb-3-community-images.s3.ap-northeast-2.amazonaws.com/profile123.jpg"
  },
  "timestamp": "2025-10-21T10:00:00"
}
```

---

### 2.3 사용자 정보 수정
**Endpoint:** `PATCH /users/{userID}`

**헤더:** Authorization: Bearer {access_token}

**Content-Type:** `application/json`

**Request Body:**
```json
{
  "nickname": "새닉네임",
  "imageId": 123,
  "removeImage": false
}
```

**필드:**
- `nickname` (String, 선택) - 닉네임 (10자 이내)
- `imageId` (Number, 선택) - Presigned URL로 업로드한 이미지 ID
- `removeImage` (Boolean, 선택) - 이미지 제거 플래그

**이미지 업로드 플로우:**
1. `GET /images/presigned-url?filename=profile.jpg` → Presigned URL + imageId 받기
2. 클라이언트가 Presigned URL로 S3 직접 업로드
3. `PATCH /users/{id}` 시 imageId를 JSON으로 전송

**이미지 처리:**
- `imageId: 123` - 새 이미지로 교체 (기존 이미지는 고아 처리 → TTL 1시간 복원)
- `removeImage: true` - 기존 이미지 제거 (TTL 1시간 후 배치 삭제)
- 둘 다 없음 - 이미지 유지
- **주의:** removeImage와 imageId 동시 전달 시 **imageId가 우선 적용**됨

**응답:**
- 200: `update_profile_success` → 수정된 정보 반환
- 404: USER-001 (User not found), IMAGE-001 (Image not found)
- 409: USER-003 (Nickname exists)
- 401/403/500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "update_profile_success",
  "data": {
    "userId": 1,
    "email": "test@startupcode.kr",
    "nickname": "새닉네임",
    "profileImage": "https://ktb-3-community-images.s3.ap-northeast-2.amazonaws.com/profile456.jpg"
  },
  "timestamp": "2025-10-21T10:00:00"
}
```

---

### 2.4 비밀번호 변경
**Endpoint:** `PATCH /users/{userID}/password`

**헤더:** Authorization: Bearer {access_token}

**Request:** `{ "new_password": "...", "new_password_confirm": "..." }`

**필수:** new_password(String), new_password_confirm(String)

**응답:**
- 200: `update_password_success`
- 404: USER-001 (User not found)
- 400: USER-004 (Password policy), USER-006 (Password mismatch)
- 401/403/500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "update_password_success",
  "data": null,
  "timestamp": "2025-10-21T10:00:00"
}
```

---

### 2.5 회원 탈퇴 (비활성화)
**Endpoint:** `PUT /users/{userID}`

**헤더:** Authorization: Bearer {access_token}

**응답:**
- 200: `account_deactivated_success`
- 404: USER-001 (User not found)
- 401/403/500: [공통 에러 코드](#응답-코드) 참조

**Note:** Request Body 없이 서버에서 자동으로 INACTIVE 상태로 변경

---

## 3. 게시글 (Posts)

### 3.1 게시글 목록 조회
**하이브리드 페이지네이션**: latest(cursor), likes(offset)

#### latest (최신순, Cursor 방식)
**Endpoint:** `GET /posts?cursor=123&limit=10&sort=latest`

**쿼리:** cursor(Long, optional), limit(Number, default 10), sort=latest

**응답:**
- 200: `get_posts_success` → posts[], nextCursor, hasMore
- 400/500: [공통 에러 코드](#응답-코드) 참조

**데이터 구조 (Cursor):**
```json
{
  "posts": [{
    "postId": 123,
    "title": "...",
    "content": "...",
    "createdAt": "2025-09-30T10:00:00Z",
    "updatedAt": "2025-09-30T10:00:00Z",
    "author": { "userId": 1, "nickname": "...", "profileImage": "..." },
    "stats": { "likeCount": 42, "commentCount": 15, "viewCount": 230 }
  }],
  "nextCursor": 100,
  "hasMore": true
}
```

**참고:**
- cursor=null → 첫 페이지
- nextCursor=null → 마지막 페이지
- hasMore=false → 더 이상 데이터 없음

**⚠️ Breaking Change (Phase 5):**
- latest 정렬은 **offset 파라미터를 지원하지 않습니다**
- `GET /posts?offset=20&sort=latest` 요청 시 offset은 무시되고 첫 페이지 반환
- 무한 스크롤 구현 시 cursor 방식을 사용하세요

#### likes (인기순) - ⚠️ 미구현

**Endpoint:** `GET /posts?offset=0&limit=10&sort=likes`

**상태:** 🚧 미구현 (Phase 6 이후 작업 예정)

**계획된 스펙:**
- **정렬 기준**: `stats.like_count` DESC, `created_at` DESC
- **페이지네이션**: Offset 방식 (cursor 방식은 Phase 7 이후)
- **응답 구조**: latest와 동일한 posts[] 배열 + pagination.total_count

**현재 동작:**
- `?sort=likes` 파라미터 전달 시 → latest 정렬과 동일하게 처리됨
- 좋아요 순 정렬이 필요한 경우 클라이언트에서 정렬 수행 필요

---

### 3.2 특정 게시글 상세 조회
**Endpoint:** `GET /posts/{postId}`

**응답:**
- 200: `get_post_detail_success` → 게시글 상세 정보 (작성자 정보 포함)
- 404: POST-001 (Post not found)
- 500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "get_post_detail_success",
  "data": {
    "postId": 123,
    "title": "게시글 제목",
    "content": "게시글 내용",
    "author": {
      "userId": 1,
      "nickname": "작성자",
      "profileImage": "https://..."
    },
    "stats": {
      "viewCount": 100,    // ⚠️ 클라이언트는 UI에 101(+1) 표시
      "likeCount": 42,
      "commentCount": 15
    },
    "isLikedByCurrentUser": true,  // 현재 사용자의 좋아요 여부 (로그인: true/false, 비로그인: null)
    "createdAt": "2025-10-18T10:00:00",
    "updatedAt": "2025-10-18T10:00:00"
  },
  "timestamp": "2025-10-18T10:00:00"
}
```

**구현 노트 (Optimistic Update 패턴):**

서버는 조회수 증가 전 값을 응답하고, 클라이언트가 UI에서 +1 보정합니다.

| 항목 | 설명 |
|------|------|
| 서버 처리 | JPQL UPDATE로 조회수 증가 (영속성 컨텍스트 우회) |
| 응답 값 | 증가 전 값 반환 (stale viewCount) |
| 클라이언트 | UI에서 응답값 + 1 표시 (detail.js:505) |
| 동기화 | F5 새로고침 시 정확한 값으로 동기화 |

**시나리오:**
```
1. 첫 방문: DB 100 → UPDATE 101 → 응답 100 → UI 101 ✅
2. F5: DB 101 → UPDATE 102 → 응답 101 → UI 102 ✅
3. 다중 탭 (주의):
   - 탭1: DB 100 → UPDATE 101 → 응답 100 (늦게 도착) → UI 101
   - 탭2: DB 101 → UPDATE 102 → 응답 101 (먼저 도착) → UI 102
   - 일시적 불일치 발생 가능, F5로 해결
```

**설계 배경:**
- Phase 5에서 좋아요/댓글과 패턴 통일 (Optimistic Update)
- detached entity 이슈 해결 (refresh() 제거)
- 동시성 제어는 JPQL UPDATE로 보장

**Rollback 가이드:**

정확한 조회수가 즉시 필요한 요구사항 발생 시:

```java
// PostService.java:getPostDetail() 메서드에 추가
postStatsRepository.incrementViewCount(postId);

if (post.getStats() != null) {
    entityManager.refresh(post.getStats());  // 추가
}

return PostResponse.from(post);
```

단, refresh() 사용 시:
- `clearAutomatically=false` 유지 (detached entity 방지)
- detail.js의 `+1` 제거 필요: `updateViewCount(stats.viewCount)`

---

### 3.3 새 게시글 작성
**Endpoint:** `POST /posts`

**헤더:** Authorization: Bearer {access_token}

**Request:** `{ "title": "...", "content": "...", "imageId": 1 }`

**필수:** title(String), content(String)
**선택:** imageId(Number) - POST /images로 먼저 업로드 필요

**응답:**
- 201: `create_post_success` → postId 반환
- 404: IMAGE-001 (Image not found)
- 400/401/500: [공통 에러 코드](#응답-코드) 참조

---

### 3.4 게시글 수정
**Endpoint:** `PATCH /posts/{postId}`

**헤더:** Authorization: Bearer {access_token}

**Request:**
```json
{
  "title": "...",
  "content": "...",
  "imageId": 1,
  "removeImage": false
}
```

**선택:** title(String), content(String), imageId(Number), removeImage(Boolean)  
**참고:** PATCH는 부분 업데이트, 최소 1개 필드 필요 , 변경이 없을 경우 WAS 내에서 처리바람.

**이미지 처리:**
- `removeImage: true` - 기존 이미지 제거 (브릿지 삭제 + TTL 1시간 후 배치 삭제)
- `imageId: 123` - 새 이미지로 교체 (기존 이미지는 고아 처리 → TTL 복원)
- 둘 다 없음 - 이미지 유지
- **주의:** removeImage와 imageId 동시 전달 시 imageId가 우선 적용됨

**응답:**
- 200: `update_post_success` → 수정된 정보 반환
- 404: POST-001 (Post not found), IMAGE-001 (Image not found)
- 403: POST-002 (Owner mismatch)
- 400/401/500: [공통 에러 코드](#응답-코드) 참조

---

### 3.5 게시글 삭제
**Endpoint:** `DELETE /posts/{postId}`

**헤더:** Authorization: Bearer {access_token}

**응답:**
- 204: 삭제 성공 (응답 body 없음)
- 404: POST-001 (Post not found)
- 403: POST-002 (Owner mismatch)
- 401/500: [공통 에러 코드](#응답-코드) 참조

**Note:** Soft Delete - Request Body 없이 서버에서 자동으로 DELETED 상태로 변경

---

## 4. 이미지 (Images)

### 4.1 이미지 업로드
**Endpoint:** `POST /images`

**헤더:** Authorization: Bearer {access_token}, Content-Type: multipart/form-data

**Request:** file(File) - 업로드할 이미지 파일

**제약:** JPG/PNG/GIF, 최대 5MB

**응답:**
- 201: `upload_image_success` → imageId, imageUrl 반환
- 413: IMAGE-002 (File too large)
- 400: IMAGE-003 (Invalid file type)
- 401/500: [공통 에러 코드](#응답-코드) 참조

---

### 4.2 이미지 메타데이터 등록 (Lambda 연동)
**Endpoint:** `POST /images/metadata`

**헤더:** Authorization: Bearer {access_token}

**Request:**
```json
{
  "imageUrl": "https://s3.amazonaws.com/bucket/...",
  "fileSize": 102400,
  "originalFilename": "profile.jpg"
}
```

**필수:** imageUrl(String)
**선택:** fileSize(Integer, 양수), originalFilename(String)

**용도:** Lambda에서 S3에 이미지 업로드 후 메타데이터 등록

**응답:**
- 201: `register_image_metadata_success` → imageId, imageUrl 반환
- 400: COMMON-001 (Invalid input)
- 401/500: [공통 에러 코드](#응답-코드) 참조

**Rate Limit:** 10회/분 (Tier 2)

---

### 4.3 Presigned URL 발급
**Endpoint:** `GET /images/presigned-url`

**헤더:** Authorization: Bearer {access_token | guest_token}

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| filename | String | ✅ | 원본 파일명 (확장자 포함) |
| content_type | String | ❌ | MIME type (기본: 확장자 기반 추론) |

**제약:**
- 허용 확장자: .jpg, .jpeg, .png, .gif
- Presigned URL 유효기간: 15분
- 이미지 TTL: 1시간 (연결 시 해제)

**응답:**
- 201: `presigned_url_generated`
- 400: IMAGE-003 (Invalid file type), COMMON-001 (Filename required)
- 401/500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "presigned_url_generated",
  "data": {
    "imageId": 123,
    "uploadUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/images/2025/12/01/uuid.jpg?X-Amz-Algorithm=...",
    "s3Key": "images/2025/12/01/uuid.jpg",
    "expiresAt": "2025-12-01T15:30:00"
  },
  "timestamp": "2025-12-01T15:15:00"
}
```

**클라이언트 사용법:**
```javascript
// 1. Presigned URL 발급
const { imageId, uploadUrl } = await fetch('/images/presigned-url?filename=profile.jpg', {
  headers: { Authorization: `Bearer ${token}` }
}).then(r => r.json()).then(d => d.data);

// 2. S3 직접 업로드 (PUT)
await fetch(uploadUrl, {
  method: 'PUT',
  body: imageFile,
  headers: { 'Content-Type': 'image/jpeg' }
});

// 3. 회원가입/게시글 작성 시 imageId 사용
await fetch('/users/signup', {
  method: 'POST',
  body: JSON.stringify({ email, password, nickname, imageId })
});
```

**Rate Limit:** 10회/분 (Tier 2)

---

## 5. 댓글 (Comments)

**댓글 객체:** `{ commentId, content, createdAt, updatedAt, author: { userId, nickname, profileImage } }`

### 5.1 댓글 목록 조회
**Endpoint:** `GET /posts/{postId}/comments?offset=0&limit=10`

**쿼리:** offset(Number), limit(Number)

**정렬:** 작성일시 내림차순 (최신 댓글 먼저)

**응답:**
- 200: `get_comments_success` → comments[], pagination.total_count
- 404: POST-001 (Post not found)
- 400/500: [공통 에러 코드](#응답-코드) 참조

---

### 5.2 댓글 작성
**Endpoint:** `POST /posts/{postId}/comments`

**헤더:** Authorization: Bearer {access_token}

**Request:** `{ "comment": "..." }`

**필수:** comment(String) - 200자 제한

**응답:**
- 201: `create_comment_success` → commentId, comment, author 반환
- 404: POST-001 (Post not found)
- 400/401/500: [공통 에러 코드](#응답-코드) 참조

---

### 5.3 댓글 수정
**Endpoint:** `PATCH /posts/{postId}/comments/{commentId}`

**헤더:** Authorization: Bearer {access_token}

**Request:** `{ "comment": "..." }`

**필수:** comment(String) - 200자 제한

**응답:**
- 200: `update_comment_success` → 수정된 댓글 정보 반환
- 404: POST-001 (Post not found), COMMENT-001 (Comment not found)
- 403: COMMENT-002 (Owner mismatch)
- 400/401/500: [공통 에러 코드](#응답-코드) 참조

---

### 5.4 댓글 삭제
**Endpoint:** `DELETE /posts/{postId}/comments/{commentId}`

**헤더:** Authorization: Bearer {access_token}

**응답:**
- 204: 삭제 성공 (응답 body 없음)
- 404: POST-001 (Post not found), COMMENT-001 (Comment not found)
- 403: COMMENT-002 (Owner mismatch)
- 401/500: [공통 에러 코드](#응답-코드) 참조

**Note:** Soft Delete - Request Body 없이 서버에서 자동으로 DELETED 상태로 변경

---

## 6. 좋아요 (Likes)

### 6.1 게시글 좋아요
**Endpoint:** `POST /posts/{postId}/like`

**헤더:** Authorization: Bearer {access_token}

**응답:**
- 200: `like_success` → 성공 메시지만 반환
- 404: POST-001 (Post not found)
- 409: LIKE-001 (Already liked)
- 401/500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "like_success",
  "data": {
    "message": "like_success"
  },
  "timestamp": "2025-10-18T10:00:00"
}
```

**변경사항 (Phase 5):**
- Optimistic Update 패턴 도입으로 like_count 응답 제거
- 클라이언트가 UI에서 즉시 +1 처리
- 다음 GET 요청 시 정확한 값 동기화

---

### 6.2 게시글 좋아요 취소
**Endpoint:** `DELETE /posts/{postId}/like`

**헤더:** Authorization: Bearer {access_token}

**응답:**
- 200: `unlike_success` → 성공 메시지만 반환
- 404: POST-001 (Post not found), LIKE-002 (Like not found)
- 401/500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "unlike_success",
  "data": {
    "message": "unlike_success"
  },
  "timestamp": "2025-10-18T10:00:00"
}
```

**변경사항 (Phase 5):**
- Optimistic Update 패턴 도입으로 like_count 응답 제거
- 클라이언트가 UI에서 즉시 -1 처리
- 다음 GET 요청 시 정확한 값 동기화

---

### 6.3 내가 좋아요 한 게시글 목록 조회
**Endpoint:** `GET /posts/users/me/likes?offset=0&limit=10`

**헤더:** Authorization: Bearer {access_token}

**쿼리:** offset(Number), limit(Number)

**응답:**
- 200: `get_liked_posts_success` → posts[], pagination.total_count
- 401/500: [공통 에러 코드](#응답-코드) 참조

---

## 7. 공통 사양

### 인증 방식 (JWT + httpOnly Cookie)

**토큰 전략:**
- **AT (Access Token)**: 응답 body → 클라이언트 localStorage → `Authorization: Bearer {token}` 헤더
- **RT (Refresh Token)**: httpOnly Cookie → 브라우저 자동 관리 → `/auth/refresh_token` 전용

**프론트엔드 구현 예시:**
```javascript
// Production: ALB 경유 (API_BASE_URL은 빈 문자열 또는 도메인)
const API_BASE_URL = window.APP_CONFIG?.API_BASE_URL || '';
const API_PREFIX = window.APP_CONFIG?.API_PREFIX || '/api/v1';

// 로그인
const response = await fetch(`${API_BASE_URL}${API_PREFIX}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  credentials: 'include',  // RT 쿠키 받기
  body: JSON.stringify({ email, password })
});

if (response.ok) {
  const data = await response.json();
  localStorage.setItem('accessToken', data.data.accessToken);  // AT를 localStorage에 저장 (MPA 환경)
  localStorage.setItem('userId', data.data.userId);
}

// API 요청 (AT를 Authorization 헤더로 전송)
const accessToken = localStorage.getItem('accessToken');
const posts = await fetch(`${API_BASE_URL}${API_PREFIX}/posts`, {
  headers: {
    'Authorization': `Bearer ${accessToken}`  // localStorage에서 가져온 AT 전송
  },
  credentials: 'include'
});

// AT 만료 시 자동 갱신
if (response.status === 401) {
  const refreshResponse = await fetch(`${API_BASE_URL}${API_PREFIX}/auth/refresh_token`, {
    method: 'POST',
    credentials: 'include'  // RT 쿠키로 자동 전송
  });

  if (refreshResponse.ok) {
    const data = await refreshResponse.json();
    accessToken = data.data.accessToken;  // 새 AT 저장
    // 원래 요청 재시도
  }
}
```

**Local Development (BE 직접 호출):**
```javascript
// Local: BE 직접 호출 시 API_PREFIX 없이 사용
const API_BASE_URL = 'http://localhost:8080';
const API_PREFIX = '';  // 로컬에서는 빈 문자열

fetch(`${API_BASE_URL}${API_PREFIX}/auth/login`, { ... });
// → http://localhost:8080/auth/login
```

**CSRF 토큰 처리 (POST/PATCH/DELETE):**
```javascript
// CSRF 토큰 추출
const csrfToken = document.cookie
  .split('; ')
  .find(row => row.startsWith('XSRF-TOKEN='))
  ?.split('=')[1];

// POST/PATCH/DELETE 요청 시 헤더 추가
const response = await fetch('http://localhost:8080/posts', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${accessToken}`,  // AT
    'X-XSRF-TOKEN': csrfToken  // CSRF 토큰
  },
  credentials: 'include',
  body: JSON.stringify(data)
});
```
- Cookie 우선, Authorization header는 하위 호환성 지원

**토큰 특성:**

| 항목 | AT | RT |
|------|----|----|
| 전달 방식 | 응답 body | httpOnly Cookie |
| 클라이언트 저장 | localStorage (MPA 환경) | 브라우저 쿠키 |
| 서버 전송 | Authorization 헤더 | Cookie 헤더 (자동) |
| 유효기간 | 15분 | 7일 |
| XSS 취약성 | 🔴 취약 (localStorage) | 🟢 안전 (httpOnly) |
| Path 제한 | 없음 | /auth/refresh_token |

### 페이지네이션

**두 가지 방식:**
- **Cursor 방식**: 최신순 정렬 (GET /posts?cursor=xxx&limit=10) - [Section 3.1 latest 참조](#latest-최신순-cursor-방식)
- **Offset 방식**: 좋아요순/댓글 목록 (GET /posts?offset=0&limit=10) - [Section 3.1 likes 참조](#likes-인기순---️-미구현)

**상세 스펙**: [Section 3.1 게시글 목록 조회](#31-게시글-목록-조회) 참조

### 표준 응답 형식
```json
{
  "message": "작업_결과_메시지",
  "data": { /* 응답 데이터 또는 null */ },
  "timestamp": "2025-10-01T14:30:00"
}
```

### 응답 코드

**HTTP 상태 코드**
- 200: OK (요청 성공)
- 201: Created (리소스 생성 성공)
- 204: No Content (성공, body 없음)
- 400: Bad Request (입력 데이터 검증 실패)
- 401: Unauthorized (인증 실패)
- 403: Forbidden (권한 없음)
- 404: Not Found (리소스 없음)
- 409: Conflict (리소스 충돌)
- 413: Payload Too Large (파일 크기 초과)
- 429: Too Many Requests (Rate Limit)
- 500: Internal Server Error (서버 오류)

**에러 코드 형식:** `{DOMAIN}-{NUMBER}` (예: USER-001, POST-001, AUTH-001)

**도메인별 에러 코드:**

#### AUTH 에러 코드 {#auth-에러-코드}
- AUTH-001: Invalid credentials (잘못된 인증 정보)
- AUTH-002: Invalid token (유효하지 않은 토큰)
- AUTH-003: Token expired (토큰 만료)
- AUTH-004: Invalid refresh token (유효하지 않은 리프레시 토큰)

#### USER 에러 코드 {#user-에러-코드}
- USER-001: Not found (사용자를 찾을 수 없음)
- USER-002: Email exists (이메일 중복)
- USER-003: Nickname exists (닉네임 중복)
- USER-004: Password policy (비밀번호 정책 위반)
- USER-005: Account inactive (계정 비활성화)
- USER-006: Password mismatch (비밀번호 불일치)
- USER-007: Unauthorized access (권한 없음)

#### POST 에러 코드 {#post-에러-코드}
- POST-001: Not found (게시글을 찾을 수 없음)
- POST-002: Owner mismatch (작성자 불일치)
- POST-003: Already deleted (이미 삭제됨)
- POST-004: Invalid status (유효하지 않은 상태)

#### COMMENT 에러 코드 {#comment-에러-코드}
- COMMENT-001: Not found (댓글을 찾을 수 없음)
- COMMENT-002: Owner mismatch (작성자 불일치)
- COMMENT-003: Already deleted (이미 삭제됨)

#### LIKE 에러 코드 {#like-에러-코드}
- LIKE-001: Already liked (이미 좋아요함)
- LIKE-002: Like not found (좋아요를 찾을 수 없음)

#### IMAGE 에러 코드 {#image-에러-코드}
- IMAGE-001: Not found (이미지를 찾을 수 없음)
- IMAGE-002: File too large (파일 크기 초과)
- IMAGE-003: Invalid file type (유효하지 않은 파일 형식)

#### COMMON 에러 코드 {#common-에러-코드}
- COMMON-001: Invalid input (입력 데이터 검증 실패)
- COMMON-002: Resource not found (리소스를 찾을 수 없음)
- COMMON-003: Resource conflict (리소스 충돌)
- COMMON-004: Too many requests (요청 횟수 초과)
- COMMON-999: Server error (서버 내부 오류)

**전체 에러 코드:** `src/main/java/com/ktb/community/enums/ErrorCode.java` 참조 (28개)

### 응답 예시

**성공 예시:**
```json
{
  "message": "login_success",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "timestamp": "2025-10-01T14:30:00"
}
```

**오류 예시:**
```json
{
  "message": "USER-002",
  "data": {
    "details": "Email already exists: test@example.com"
  },
  "timestamp": "2025-10-01T14:30:00"
}
```

---

## 8. 시스템 (System)

### 8.1 Health Check
**Endpoint:** `GET /health`

**용도:** ALB Target Group Health Check

**응답:**
- 200: `{ "status": "UP", "timestamp": "..." }`

**응답 예시:**
```json
{
  "status": "UP",
  "timestamp": "2025-10-21T10:00:00.123456"
}
```

**참고:** 인증 불필요, Rate Limit 없음

---

### 8.2 플랫폼 통계
**Endpoint:** `GET /stats`

**용도:** 랜딩페이지용 플랫폼 통계 제공

**응답:**
- 200: `get_stats_success` → 통계 데이터
- 500: [공통 에러 코드](#응답-코드) 참조

**응답 예시:**
```json
{
  "message": "get_stats_success",
  "data": {
    "totalPosts": 1234,
    "totalUsers": 567,
    "totalComments": 8901
  },
  "timestamp": "2025-10-21T10:00:00"
}
```

**데이터 설명:**
- `totalPosts`: ACTIVE 상태 게시글 수
- `totalUsers`: ACTIVE 상태 사용자 수
- `totalComments`: ACTIVE 상태 댓글 수

**참고:** 인증 불필요, Rate Limit 없음