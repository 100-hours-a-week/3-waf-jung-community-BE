---
name: product-requirements
description: 기능 요구사항(FR) 및 비기능 요구사항(NFR) 정의. 새 기능 구현 전 FR-XXX-NNN 코드로 비즈니스 요구사항 확인, 우선순위(P0/P1) 파악 시 참조.
---

# 요구사항 명세서 (PRD)

**Version**: 1.0  
**Last Updated**: 2025-12-01  
**Status**: 완료 (Phase 3.7까지 반영)  

---

# PRD.md - Product Requirements Document
## 문서 정보
| 항목 | 내용 |
|------|------|
| 프로젝트명 | DC2 Community Platform |
| 버전 | 1.1 |
| 문서 유형 | Product Requirements Document |
---

## 1. 제품 개요

### 1.1 프로젝트 목적
사용자들이 자유롭게 게시글을 작성하고, 댓글을 통해 소통하며, 좋아요를 통해 공감을 표현할 수 있는 커뮤니티 백엔드 서비스

### 1.2 프로젝트 범위
- **대상**: 일반 사용자, 관리자(향후)
- **플랫폼**: RESTful API 백엔드
- **배포**: 백엔드 API 서버

---

## 2. 기능 요구사항

### 2.1 인증 및 권한 관리

#### FR-AUTH-001: 회원가입 (P0)
**설명**: 신규 사용자가 이메일과 비밀번호로 계정 생성

**입력**: email(필수,유니크), password(필수,8-20자), nickname(필수,10자,유니크), profile_image(선택, File)  
**요청 형식**: multipart/form-data  
**출력**: Access Token(15분), Refresh Token(7일)  
**검증**: 이메일/닉네임 중복, 비밀번호 정책(대/소/특수문자 각 1개+), 이미지 형식(JPG/PNG/GIF), 파일 크기(최대 5MB)  
**에러**: 409(중복), 400(유효성), 413(파일 크기 초과), 400(유효하지 않은 파일 형식)

---

#### FR-AUTH-002: 로그인 (P0)
**설명**: 등록된 사용자가 이메일과 비밀번호로 로그인

**입력**: email, password  
**출력**: Access Token, Refresh Token  
**검증**: 이메일/비밀번호 일치, 계정 상태(ACTIVE만 허용)  
**에러**: 401(잘못된 인증), 403(비활성 계정)

---

#### FR-AUTH-003: 로그아웃 (P0)
**설명**: 로그인된 사용자가 로그아웃

**입력**: Refresh Token  
**처리**: Refresh Token을 DB에서 삭제  
**출력**: 성공 메시지

---

#### FR-AUTH-004: Access Token 재발급 (P0)
**설명**: Refresh Token으로 새 Access Token 발급

**입력**: Refresh Token  
**출력**: 새 Access Token  
**검증**: Refresh Token 유효성, 만료 확인  
**에러**: 401(유효하지 않은 토큰)

---

### 2.2 사용자 관리

#### FR-USER-001: 사용자 프로필 조회 (P0)
**입력**: User ID  
**출력**: nickname, email, profile_image  
**권한**: 누구나 (공개 프로필)

---

#### FR-USER-002: 사용자 프로필 수정 (P0)
**입력**: nickname(선택,10자), profile_image(선택, File), removeImage(선택, Boolean)  
**요청 형식**: multipart/form-data  
**출력**: 수정된 프로필  
**검증**: 본인만 수정, 닉네임 중복, 이미지 형식(JPG/PNG/GIF), 파일 크기(최대 5MB)  
**이미지 처리**:
- profileImage 제공: 새 이미지로 교체 (기존 이미지는 고아 처리 → TTL 1시간 복원)
- removeImage: true - 기존 이미지 제거 (TTL 1시간 후 배치 삭제)
- 둘 다 없음: 이미지 유지
- **주의**: removeImage와 profileImage 동시 전달 시 profileImage가 우선 적용됨  
**에러**: 403(권한 없음), 409(닉네임 중복), 413(파일 크기 초과), 400(유효하지 않은 파일 형식)

---

#### FR-USER-003: 비밀번호 변경 (P0)
**입력**: new_password, new_password_confirm  
**검증**: 비밀번호 정책, 일치 확인  
**에러**: 400(불일치)

---

#### FR-USER-004: 회원 탈퇴 (P0)
**설명**: 계정 비활성화 (데이터는 유지)

**처리**: 사용자 상태 → INACTIVE  
**참고**: Soft Delete, GDPR 고려 필요

---

### 2.3 게시글 관리

#### FR-POST-001: 게시글 작성 (P0)
**입력**: title(필수,27자), content(필수,LONGTEXT), image_id(선택,다중)  
**출력**: 게시글 ID  
**초기 상태**: ACTIVE  
**권한**: 인증된 사용자만
**참고**: 현재는 단일 이미지만 지원하며 다중 이미지는 추후 확장 예정

---

#### FR-POST-002: 게시글 목록 조회 (P0)
**입력**: 
- **최신순 (sort=latest)**: cursor(선택), limit(기본 10)
- **좋아요순 (sort=likes)**: offset(기본 0), limit(기본 10)

**출력**: 
- **최신순**: 게시글 목록, nextCursor, hasMore (Cursor 페이지네이션)
- **좋아요순**: 게시글 목록, total_count (Offset 페이지네이션)

**필터링**: ACTIVE만  
**권한**: 누구나  
**참고**: 하이브리드 전략 - 최신순은 무한 스크롤(cursor), 좋아요순은 페이지 번호(offset)

---

#### FR-POST-003: 게시글 상세 조회 (P0)
**입력**: 게시글 ID  
**출력**: 게시글 정보, 작성자 정보, 통계(좋아요/댓글/조회수), 생성/수정 시간  
**부가 기능**: 조회수 자동 증가  
**권한**: 누구나

---

#### FR-POST-004: 게시글 수정 (P0)
**입력**: title(선택), content(선택), image_id(선택)  
**출력**: 수정된 게시글  
**검증**: 작성자 본인만, 최소 1개 필드 필요  
**에러**: 403(권한 없음), 404(게시글 없음)
**참고**: PATCH는 부분 업데이트이므로 모든 필드 선택

---

#### FR-POST-005: 게시글 삭제 (P0)
**처리**: 상태 → DELETED (Soft Delete)  
**검증**: 작성자 본인만  
**에러**: 403(권한 없음)

---

### 2.4 댓글 관리

#### FR-COMMENT-001: 댓글 작성 (P0)
**입력**: 게시글 ID, content(필수,200자)  
**출력**: 댓글 정보(작성자 포함)  
**부가 기능**: 댓글 수 자동 증가  
**권한**: 인증된 사용자만

---

#### FR-COMMENT-002: 댓글 목록 조회 (P0)
**입력**: 게시글 ID, offset(기본 0), limit(기본 10)  
**출력**: 댓글 목록(작성자 포함), total_count  
**정렬**: 작성일시 오름차순  
**필터링**: ACTIVE만  
**권한**: 누구나

---

#### FR-COMMENT-003: 댓글 수정 (P0)
**입력**: content(200자)  
**출력**: 수정된 댓글  
**검증**: 작성자 본인만  
**에러**: 403(권한 없음)

---

#### FR-COMMENT-004: 댓글 삭제 (P0)
**처리**: 상태 → DELETED (Soft Delete)  
**부가 기능**: 댓글 수 자동 감소  
**검증**: 작성자 본인만  
**에러**: 403(권한 없음)

---

### 2.5 좋아요 기능

#### FR-LIKE-001: 게시글 좋아요 추가 (P0)
**입력**: 게시글 ID  
**출력**: 성공 메시지 (like_count는 클라이언트가 UI에서 +1 처리)  
**검증**: 한 사용자당 한 게시글에 한 번만
**부가 기능**: 좋아요 수 자동 증가 (DB 원자적 UPDATE)  
**에러**: 409(중복 좋아요)
**권한**: 인증된 사용자만
**참고**: Optimistic Update 패턴 (API.md Section 6.1)

---

#### FR-LIKE-002: 게시글 좋아요 취소 (P0)
**입력**: 게시글 ID  
**출력**: 성공 메시지 (like_count는 클라이언트가 UI에서 -1 처리)  
**부가 기능**: 좋아요 수 자동 감소 (DB 원자적 UPDATE)  
**권한**: 인증된 사용자만  
**참고**: Optimistic Update 패턴 (API.md Section 6.2)

---

#### FR-LIKE-003: 좋아요한 게시글 목록 조회 (P0)
**입력**: offset(기본 0), limit(기본 10)  
**출력**: 게시글 목록, total_count  
**권한**: 인증된 사용자만 자신의 목록

---

### 2.6 이미지 관리

#### FR-IMAGE-001: 이미지 정보 저장 (P0)
**입력**: image_url(필수), file_size(선택), original_filename(선택)  
**출력**: 이미지 ID  
**용도**: 프로필 이미지, 게시글 이미지

---

#### FR-IMAGE-002: 게시글 이미지 관리 (P0)
**설명**: 게시글에 여러 이미지를 순서대로 연결

**처리**: post_images 브릿지 테이블, display_order 관리

---

#### FR-IMAGE-003: 이미지 업로드 (P0)
**설명**: 서버 측 이미지 업로드 및 검증 처리

**입력**: multipart/form-data (이미지 파일)  
**출력**: image_id, image_url  
**검증**: 파일 형식 (JPG/PNG/GIF), 파일 크기 (최대 5MB)  
**처리**: S3 직접 저장 + TTL 1시간 (사용 시 expires_at NULL 전환)  
**에러**: 413 (파일 크기 초과), 400 (유효하지 않은 파일 형식)  
**2가지 패턴**: **@docs/be/LLD.md Section 7.5** (Multipart 직접 vs 2단계 업로드)

---

## 3. 비기능 요구사항

### 3.1 보안 (Security)

**NFR-SEC-001: 인증 및 권한**
- JWT 기반 토큰 인증
- Access Token: 15분, Refresh Token: 7일
- **토큰 전달**: httpOnly Cookie (XSS 방어)
  - access_token: HttpOnly, SameSite=Strict, Path=/, 15분
  - refresh_token: HttpOnly, SameSite=Strict, Path=/auth/refresh_token, 7일
- **Refresh Token DB 관리**: user_tokens 테이블 (갱신/무효화)
- **보안 강화**: JavaScript 접근 불가 (httpOnly), CSRF 방어 (SameSite=Strict)

**NFR-SEC-002: 비밀번호 보안**
- BCrypt 암호화
- 정책: 8-20자, 대/소/특수문자 각 1개+
- **구현**: **@docs/be/LLD.md Section 6.4** (정규식 포함)

**NFR-SEC-003: API 보안**
- HTTPS(배포 시), CORS 설정
- SQL Injection 방지(JPA Parameterized Query)
- XSS 방지(입력 검증)

**NFR-SEC-004: Rate Limiting (3-Tier 전략)**
- 알고리즘: Token Bucket (Bucket4j)
- 저장소: 인메모리 (Caffeine Cache), 추후 Redis
- 엔드포인트별 제한: 5~200회/분 (Tier별 차등 적용)
- **3-Tier 전략 및 구현**: **@docs/be/LLD.md Section 6.5** 참조

---

### 3.2 성능 (Performance)

**NFR-PERF-001: 응답 시간**
- API 평균: < 500ms
- 게시글 목록: < 300ms
- 게시글 상세: < 200ms

**NFR-PERF-002: 데이터베이스 최적화**
- 인덱스 활용(@docs/be/DDL.md)
- N+1 방지(Fetch Join)
- 커넥션 풀(HikariCP)

**구현**: @docs/be/LLD.md Section 12

**NFR-PERF-003: 페이지네이션**
- 하이브리드 전략: latest (Cursor), likes (Offset)
- **구현 및 장단점**: **@docs/be/LLD.md Section 7.3** 참조

---

### 3.3 확장성 (Scalability)

**NFR-SCALE-001: 아키텍처**
- 3-Layer Architecture (Controller-Service-Repository)
- 관심사 분리, 느슨한 결합

**NFR-SCALE-002: 캐싱 전략 (추후)**
- Redis 도입 고려
- 토큰 관리, 자주 조회되는 데이터

---

### 3.4 신뢰성 (Reliability)

**NFR-REL-001: 에러 처리**
- 중앙화된 예외 처리(GlobalExceptionHandler)
- 에러 메시지 코드화
- 표준 HTTP 상태 코드

**NFR-REL-002: 로깅**
- API 요청/응답, 에러 스택 트레이스
- 로그 레벨: INFO, DEBUG, ERROR

**NFR-REL-003: 데이터 무결성**
- Foreign Key 제약조건
- 트랜잭션 관리
- 동시성 제어: PostStats (좋아요/댓글/조회수) 원자적 UPDATE
- **구현 상세**: **@docs/be/LLD.md Section 7.2, 12.3** (JPQL 코드 포함)

---

### 3.5 유지보수성 (Maintainability)

**NFR-MAINT-001: 코드 품질**
- Lombok으로 보일러플레이트 감소
- 의미 있는 네이밍
- 주석 및 문서화

**NFR-MAINT-002: 테스트**
- 커버리지 60% 이상
- Service Layer 중점 테스트
- JUnit 5 + Mockito

**NFR-MAINT-003: 문서화**
- API 명세(**@docs/be/API.md**)
- DB 스키마(**@docs/be/DDL.md**)
- 개발 가이드(**@CLAUDE.md**)

---

## 4. 데이터 요구사항

### 4.1 데이터 모델

**핵심 엔티티**: User, Post, Comment, PostLike, Image, UserToken, PostStats, PostImage

**핵심 관계**:
- User 1:N → Post, Comment, PostLike
- Post 1:1 → PostStats
- Post 1:N → Comment
- Post M:N → Image (via PostImage)

**상세**: **@docs/be/DDL.md** (ERD, 테이블 구조, Foreign Keys)

### 4.2 데이터 보존

**Soft Delete**: User, Post, Comment (상태만 변경: DELETED/INACTIVE)  
**Hard Delete**: UserToken (만료 시 실제 삭제, 정기 배치)  
**법적 요구사항**: GDPR 등 고려 필요

---

## 5. 제약사항 및 가정

### 5.1 기술적 제약사항
**기술 스택**: **@docs/be/LLD.md Section 1** 참조

### 5.2 비즈니스 제약사항

**현재 범위 (2025-12-09 기준):**
- ✅ **백엔드 API**: Spring Boot 3.5.6 기반 REST API 완성
- ✅ **프론트엔드**: Express.js 서버 렌더링 MPA (별도 프로젝트)
- ✅ **이미지 저장소**: AWS S3 (하이브리드 업로드 방식)
  - **현재**: Presigned URL (클라이언트 직접 S3 업로드) + Multipart (서버 경유) 병행
  - **변경 이력**: 초기 로컬 스토리지 → S3 Multipart (Phase 3.5) → Presigned URL 추가 (Phase 3.7)
  - **상세**: **@docs/be/LLD.md Section 7.5** (5가지 이미지 업로드 패턴)
- ✅ **배포 인프라**: AWS (ALB + EC2 + ECR + RDS MySQL + S3)
  - **현재**: GitHub Actions + Jenkins CI/CD 파이프라인 (Phase 4 완료)
  - **변경 이력**: 배포 미정 → GitHub Actions SSH → Jenkins SSH (동적 배포)
  - **상세**: **@docs/deployment/CI-CD.md**

**토큰 관리:**
- **현재**: RDB 저장 (user_tokens 테이블)
- **향후**: Redis 전환 고려 (Phase 6, 프로젝트 종료로 보류)

**성능 가정:**
- 초기 트래픽 낮음 (단일 서버 충분)
- 원자적 UPDATE로 동시성 제어 (PostStats)
- **확장 전략**: ALB + Auto Scaling 대응 구조 (Jenkins 동적 배포)

### 5.3 가정사항

**사용자 입력:**
- 사용자는 올바른 이메일 형식 입력
- 프론트엔드에서 1차 검증, 백엔드에서 2차 검증

**이미지 업로드:**
- **현재 (2025-12-09)**:
  - **Presigned URL**: 클라이언트가 S3에 직접 업로드 (대용량 파일)
  - **Multipart**: 서버 경유 업로드 (소규모 파일, 하위 호환성)
- **변경 이력**: "서버 경유만" → "하이브리드 방식" (Phase 3.7)
- **미사용 이미지**: TTL 1시간 후 배치 자동 삭제

**동시 접속:**
- 초기 동시 접속자 수 적음 (확장 대비 설계 완료)

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|-----------|
| 2025-09-30 | 1.0 | 초기 PRD 작성 |
| 2025-10-04 | 1.1 | 최적화 버전 (참조 기반, LLD 섹션 참조 수정, API 스펙 일치) |
| 2025-10-10 | 1.2 | Phase 3 대비 문서 정합성 개선 (이미지 업로드, Rate Limiting 키 방식 반영) |
| 2025-10-22 | 1.3 | 중복 제거 및 참조 최적화 (기술 스택, 비밀번호, Rate Limiting, 페이지네이션, 동시성, 이미지 업로드) |
| 2025-11-03 | 1.4 | FR-USER-002 removeImage 파라미터 추가 (이미지 제거 기능 문서화) |
| 2025-12-09 | 1.5 | Section 5 제약사항 최신화 (S3 Presigned URL, CI/CD 파이프라인, 변경 이력 명시) |