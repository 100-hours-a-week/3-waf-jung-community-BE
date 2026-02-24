---
name: implementation-plan
description: Phase별 구현 로드맵 및 진행 상황. 현재 Phase 확인, 체크리스트 업데이트, FR-Phase 매핑 파악 시 참조.
---

# PLAN.md - DC2 Community 프로젝트 구현 계획

**Version**: 1.0  
**Last Updated**: 2025-12-09  
**Status**: Phase 5 부분 완료  

---

## 프로젝트 개요

**프로젝트명**: DC2(Developer Community Club) Community Platform  
**기술 스택 및 아키텍처**: **@docs/be/LLD.md Section 1-2** 참조

---

## 현재 진행 상황

- **Phase 1 완료** ✅
- **Phase 2 완료** ✅ (Week 2-3)
- **Phase 3 완료** ✅ (Week 4-5)
- **Phase 3.5 완료** ✅ (S3 이미지 업로드)
- **Phase 3.5.1 완료** ✅ (Presigned URL 방식으로 변경)
- **Phase 4 완료** ✅ (통계 및 배치)

---

## 전체 로드맵

| Phase | Week | 목표 | FR 범위 | 상태      |
|-------|------|------|---------|---------|
| Phase 1 | 1 | 기반 설정 | - | ✅ 완료    |
| Phase 2 | 2-3 | 인증/사용자 | AUTH-001~004, USER-001~004 | ✅ 완료    |
| Phase 3 | 4-5 | 게시글/댓글/좋아요 | POST-001~005, COMMENT-001~004, LIKE-001~003 | ✅ 완료    |
| Phase 3.5 | 5 | 이미지 업로드 (S3) | IMAGE-001, IMAGE-003 | ✅ 완료    |
| Phase 3.6 | 5 | Multipart 전환 + P0/P1 수정 | AUTH-001, USER-002 | ✅ 완료    |
| Phase 3.7 | - | Presigned URL 추가 | IMAGE-003 (하이브리드) | ✅ 완료    |
| Phase 4 | 6 | 통계 및 배치 | IMAGE-002 (고아 이미지) | ✅ 완료    |
| Phase 5 | 7 | 테스트/문서 + 버그 수정 | detached entity 해결 | 🔄 부분 완료 |
| Phase 6 | 8+ | Redis 도입 (조건부) | 성능 최적화 | 프로젝트 종료 |

---

## Phase 1: 프로젝트 기반 설정 ✅ 완료

**목표**: 개발 환경 구축 및 데이터베이스 스키마 구축

**완료 항목:**
- [x] Spring Boot 프로젝트 생성
- [x] MySQL 데이터베이스 설정
- [x] JPA Entity 클래스 8개
- [x] Enum 클래스 4개
- [x] 패키지 구조 설계

**참조**: **@docs/be/LLD.md Section 3** (패키지 구조), **@docs/be/DDL.md** (스키마)

---

## Phase 2: 인증 및 사용자 관리 ✅ 완료

**목표**: JWT 기반 인증 시스템 및 사용자 CRUD 구현

### FR 매핑

| FR 코드 | 기능 | 구현 위치 |
|---------|------|-----------|
| FR-AUTH-001 | 회원가입 | AuthService.signup() |
| FR-AUTH-002 | 로그인 | AuthService.login() |
| FR-AUTH-003 | 로그아웃 | AuthService.logout() |
| FR-AUTH-004 | 토큰 갱신 | AuthService.refreshToken() |
| FR-USER-001 | 사용자 조회 | UserService.getProfile() |
| FR-USER-002 | 사용자 수정 | UserService.updateProfile() |
| FR-USER-003 | 비밀번호 변경 | UserService.changePassword() |
| FR-USER-004 | 회원 탈퇴 | UserService.deactivateAccount() |

### 체크리스트

**인증 시스템:**
- [x] JwtTokenProvider (토큰 생성/검증)
- [x] UserToken 엔티티 (RDB 토큰 관리)
- [x] Spring Security 설정 (필터 체인)
- [x] BCryptPasswordEncoder (비밀번호 암호화)

**API 구현:**
- [x] 인증 API 3개 (POST /auth/login, /auth/logout, /auth/refresh_token)
- [x] 사용자 API 5개 (POST /users/signup, GET/PATCH /users/{id}, 비밀번호 변경 등)

**비즈니스 로직:**
- [x] 비밀번호 정책 검증 (8-20자, 대/소/특수문자)
- [x] 이메일/닉네임 중복 확인
- [x] Rate Limiting (3-Tier 전략)

**테스트:**
- [x] 단위 테스트 (AuthService, UserService, JwtTokenProvider, RateLimitAspect)
- [x] 통합 테스트 (UserController)

### 완료 조건
- [x] 회원가입 → 로그인 → 토큰 발급 → 인증 API 호출 플로우 작동
- [x] 비밀번호 정책 검증 통과
- [x] 모든 테스트 통과

**참조**: **@docs/be/LLD.md Section 6 (인증 및 보안), Section 7.2/12.3 (동시성 제어)**

---

## Phase 3: 게시글 및 댓글 기능 ✅ 완료

**목표**: 커뮤니티 핵심 기능 구현

### FR 매핑

| FR 코드              | 기능       | 구현 위치 |
|--------------------|----------|-----------|
| FR-POST-001~005    | 게시글 CRUD | PostService |
| FR-COMMENT-001~004 | 댓글 CRUD  | CommentService |
| FR-LIKE-001~003    | 좋아요      | LikeService |

### 체크리스트

**게시글 기능:**
- [x] PostService (CRUD, 페이지네이션, 정렬)
- [x] 권한 검증 (작성자만 수정/삭제)
- [x] 조회수 자동 증가 (PostStats 동기화)
- [x] API 6개 (POST/GET/PATCH/DELETE /posts)
- [x] PostController 구현

**댓글 기능:**
- [x] CommentService (CRUD)
- [x] 권한 검증 (작성자만 수정/삭제)
- [x] 댓글 수 자동 업데이트 (PostStats 원자적 UPDATE)
- [x] API 4개 (GET/POST/PATCH/DELETE /posts/{id}/comments)
- [x] CommentController 구현

**좋아요 기능:**
- [x] LikeService (추가/취소)
- [x] 중복 방지 (user_id, post_id UNIQUE)
- [x] 좋아요 수 자동 업데이트 (PostStats 원자적 UPDATE)
- [x] API 3개 (POST/DELETE /posts/{id}/like, GET /users/me/likes)

**Repository 계층:**
- [x] PostRepository (Fetch Join N+1 방지)
- [x] CommentRepository (Fetch Join)
- [x] PostLikeRepository (좋아요 목록)
- [x] ImageRepository (프로필 이미지 연동)

**테스트:**
- [x] 단위 테스트 (PostService, CommentService, LikeService)
- [x] Repository 테스트 (H2 환경)
- [x] N+1 문제 검증 (Fetch Join)

### 완료 조건
- [x] 게시글/댓글/좋아요 전체 플로우 작동
- [x] 권한 검증 정상 작동
- [x] 모든 테스트 통과

**참조**: **@docs/be/LLD.md Section 7 (비즈니스 로직), Section 12 (성능 최적화)**

---

## Phase 3.5: 이미지 업로드 인프라 ✅ 완료

**목표**: S3 직접 연동 이미지 업로드 시스템 구현

### FR 매핑

| FR 코드 | 기능 | 구현 위치 |
|---------|------|-----------|
| FR-IMAGE-001 | 이미지 정보 저장 | ImageRepository |
| FR-IMAGE-003 | 이미지 업로드 | ImageService |

### 체크리스트

**이미지 업로드:**
- [x] ImageService (파일 검증, S3 업로드, DB 저장)
- [x] ImageController (POST /images)
- [x] S3Client 설정 (AWS SDK v2)
- [x] 파일 검증 (크기, 형식, Magic Number)
- [x] expires_at TTL 로직 (1시간)

**통합:**
- [x] PostService 이미지 연결 (clearExpiresAt)
- [x] UserService 프로필 이미지 연결
- [x] PostImage 브릿지 테이블 처리

**테스트:**
- [x] ImageService 단위 테스트
- [x] 파일 검증 로직 테스트
- [x] S3 업로드 통합 테스트

### 완료 조건
- [x] POST /images API 작동 (multipart/form-data)
- [x] S3 업로드 및 DB 저장 확인
- [x] 게시글/프로필 이미지 연결 작동
- [x] 모든 테스트 통과

**참조**: **@docs/be/LLD.md Section 7.5** (이미지 업로드 흐름), **@docs/be/API.md Section 4.1**

---

## Phase 3.6: 회원가입/프로필 Multipart 전환 ✅ 완료

**목표**: 회원가입과 프로필 수정 시 이미지와 데이터를 함께 전송하는 자연스러운 UX 구현

### FR 매핑

| FR 코드 | 기능 | 변경 내용 |
|---------|------|-----------|
| FR-AUTH-001 | 회원가입 | 2단계 → Multipart 직접 업로드 |
| FR-USER-002 | 프로필 수정 | 2단계 → Multipart 직접 업로드 |

### 체크리스트

**DTO 수정:**
- [x] SignupRequest - profileImageId 제거
- [x] UpdateProfileRequest - profileImageId 제거

**Controller 수정:**
- [x] UserController.signup() - Multipart 적용 (@RequestPart)
- [x] UserController.updateProfile() - Multipart 적용 (@RequestPart)

**Service 수정:**
- [x] AuthService.signup() - ImageService 통합 (MultipartFile 파라미터)
- [x] UserService.updateProfile() - ImageService 통합 (MultipartFile 파라미터)

**테스트 수정:**
- [x] AuthServiceTest - MultipartFile null 처리
- [x] UserServiceTest - MultipartFile null 처리
- [x] UserControllerIntegrationTest - Manual Validation 검증

**P0/P1 수정 (중요 버그 수정):**
- [x] P0: @RequestPart Manual Validation 복원 (40자 닉네임 → 400 에러)
- [x] P1: PasswordValidator 사용으로 ErrorCode 일관성 복원 (USER-004)

### 완료 조건
- [x] Multipart 회원가입/프로필 수정 작동
- [x] Manual Validation으로 입력 검증 (Bean Validation 대체)
- [x] 모든 테스트 통과

**참조**: **@docs/be/LLD.md Section 7.5** (2가지 업로드 패턴), **@docs/be/API.md Section 2.1, 2.3**

---

## Phase 3.7: S3 Presigned URL 이미지 업로드 ✅ 완료

**목표**: 클라이언트 직접 S3 업로드로 서버 부하 감소

### FR 매핑

| FR 코드 | 기능 | 변경 내용                                     |
|---------|------|-------------------------------------------|
| FR-IMAGE-003 | 이미지 업로드 | Multipart 유지 + Presigned URL 추가( 추후 완전변경) |

### 체크리스트

**구현:**
- [x] S3Config.java - S3Presigner Bean 추가
- [x] PresignedUrlResponse.java - 응답 DTO 생성
- [x] ImageService.generatePresignedUrl() - 메서드 구현
- [x] ImageController.getPresignedUrl() - 엔드포인트 추가
- [x] JwtAuthenticationFilter - /images/presigned-url 인증 필수

**테스트:**
- [x] ImageService 단위 테스트 (generatePresignedUrl, 7개 테스트 케이스)
- [x] 확장자 검증 테스트 (.jpg, .jpeg, .png, .gif)
- [x] Rate Limit 테스트 (10회/분)

### 완료 조건
- [x] GET /images/presigned-url API 작동
- [x] 클라이언트 S3 직접 업로드 성공
- [x] 기존 POST /images API 정상 작동 (하위 호환성)
- [x] 모든 테스트 통과

**참조**: **@docs/be/LLD.md Section 7.5** (패턴 4), **@docs/be/API.md Section 4.3**

---

## Phase 4: 통계 및 배치 작업 ✅ 완료

**목표**: 게시글 통계 활용, 고아 이미지 정리 배치, CI/CD 파이프라인 구축

### 체크리스트

**통계 기능:**
- [x] PostStats 자동 업데이트 검증 (Phase 3에서 구현됨)
- [x] 통계 기반 정렬 구현 (인기순: like_count DESC)
- [x] 통계 조회 최적화 (N+1 방지)
- [x] /stats API 구현 (랜딩페이지용 플랫폼 통계)

**고아 이미지 배치:**
- [x] 배치 작업 스케줄러 (@Scheduled)
- [x] expires_at < NOW() 조건 이미지 조회
- [x] S3 파일 삭제 (⚠️ 버그 있음 - Phase 5에서 수정 예정)
- [x] DB 레코드 삭제
- [x] 배치 로그 기록

**CI/CD 인프라:**
- [x] GitHub Actions 워크플로우 (.github/workflows/cd.yml)
- [x] Jenkins 파이프라인 (Jenkinsfile.backend)
- [x] AWS ECR 설정 (OIDC 인증)
- [x] ALB 경로 기반 라우팅 (/api/v1/* → Backend)

**테스트:**
- [x] 통계 정렬 테스트 (Phase 3에서 완료)
- [x] 배치 작업 단위 테스트
- [x] TTL 만료 시나리오 검증

### 완료 조건
- [x] 고아 이미지 배치 작업 스케줄 실행
- [x] 배치 로그 확인
- [x] CI/CD 파이프라인 프로덕션 배포 검증
- [x] /stats API 작동 확인

**참조**:
- **@docs/be/LLD.md Section 7.5** (고아 이미지 처리)
- **@docs/deployment/CI-CD.md** (GitHub Actions + Jenkins 파이프라인)
- **@docs/be/LLD.md Section 2.1-2.2** (ALB 경로 기반 라우팅)

---

## Phase 5: 테스트 및 문서화

**목표**: 품질 확보 및 문서 정리

### 체크리스트

**Phase 4 버그 수정 (중요 트러블슈팅):**
- [x] PostService detached entity 문제 해결
  - 해결책: `clearAutomatically = false` 적용
  - 부가효과: Optimistic Update 패턴 도입 (DB 통신 17% 감소)

**Optimistic Update 패턴:**
- [x] 좋아요 API 응답 간소화
- [x] 클라이언트 UI 즉시 업데이트
- [x] 원자적 쿼리 유지

**페이지네이션:**
- [x] Cursor 페이지네이션 전환 (최신순만)
- [ ] Cursor 페이지네이션 확장 (likes 정렬, 미완료)
- [ ] GET /posts/users/me/likes cursor 전환 (미완료)

**미완료 항목 (프로젝트 종료):**

*테스트:*
- [ ] 전체 Service Layer 테스트 (현재 진행률: ~40%)
- [ ] Repository Layer 테스트 (현재 진행률: ~20%)
- [ ] 통합 테스트 주요 플로우

*문서화:*
- [ ] @docs/be/API.md 최종 검토
- [ ] Postman Collection 작성
- [ ] README 업데이트

*코드 품질:*
- [ ] 코드 리뷰 및 리팩토링
- [ ] 네이밍 컨벤션 통일
- [ ] 불필요한 주석 제거

**종료 사유**: 핵심 비즈니스 로직 구현 완료, 추가 테스트는 향후 과제로 이관

### 완료 조건
- 전체 테스트 커버리지 60% 이상
- API 문서 최신화
- 코드 리뷰 완료

---

## 개발 규칙

**Phase 완료 기준:**
1. 모든 체크리스트 완료
2. 단위 테스트 통과 (Service 80%+)
3. 완료 조건 검증
4. PLAN.md 체크박스 업데이트

**문서 동기화:**
- 자동: Documentation Sync Manager 스킬 (`문서 동기화`, pre-commit hook)
- 참조: `.claude/skills/Documentation Sync Manager/SKILL.md`

---

## 제약사항 (설계 배경)

**기술 제약**: 토큰 RDB 저장 → Redis 전환, S3 직접 저장  
**성능 가정**: 초기 트래픽 낮음, 단일 서버, 원자적 UPDATE  
**데이터 정책**: Soft Delete (User/Post/Comment), Hard Delete (UserToken/Image)

**상세**: **@docs/be/PRD.md Section 5**, **@docs/be/LLD.md Section 7.5, 12.3**

---

## 주요 리스크

| 리스크 | 대응 방안 |
|--------|-----------|
| JWT RDB 성능 저하 | 인덱스 최적화, Redis 전환 |
| 동시성 이슈 | 원자적 UPDATE, 락 전략 |
| 고아 이미지 누적 | TTL 기반 배치 삭제 (Phase 4) |
| S3 비용 초과 | Free Tier 모니터링, 압축 최적화 |

---

## 참고 문서

- **요구사항**: @docs/be/PRD.md (FR/NFR 코드)
- **설계**: @docs/be/LLD.md (아키텍처, 패턴)
- **스키마**: @docs/be/DDL.md
- **API**: @docs/be/API.md
- **가이드**: @CLAUDE.md