---
name: low-level-design
description: 아키텍처 및 구현 패턴 상세 설계. JWT 인증, Rate Limiting, 동시성 제어, 이미지 업로드, 페이지네이션 등 구현 방법 확인 시 참조.
---

# LLD.md - Low Level Design Document

## 문서 정보

| 항목 | 내용 |
|------|------|
| 프로젝트명 | DC2 Community Platform |
| 버전 | 2.0 |
| 문서 유형 | Low Level Design Document |

---

## 1. 기술 스택

**백엔드:** Spring Boot 3.5.6, Java 21 LTS, Gradle 8.x  
**데이터베이스:** MySQL 8.0+, JPA (Hibernate), HikariCP  
**보안:** Spring Security, JWT, BCrypt  
**Storage:** AWS S3 (이미지 직접 저장, Free Tier)  
**추후:** Redis (토큰 캐싱)

**패키지 루트:** `com.ktb.community`

---

## 2. 시스템 아키텍처

### 2.1 전체 구조

```
                              ┌─────────────────────────────────┐
                              │         AWS ALB                 │
        Client Request        │   Path-based Routing            │
             │                │                                 │
             ▼                │   /api/v1/* → BE (path rewrite) │
        ┌──────────┐          │   /*        → FE                │
        │/api/v1/  │─────────►│                                 │
        │auth/login│          │         ┌─────────┴─────────┐   │
        └──────────┘          │         ▼                   ▼   │
                              │   ┌──────────┐       ┌──────────┐
                              │   │ BE(8080) │       │ FE(3000) │
                              │   └────┬─────┘       └──────────┘
                              └────────┼─────────────────────────┘
                                       ↓
                         Controller Layer (요청/응답 처리)
                                       ↓
                         Service Layer (비즈니스 로직, @Transactional)
                                       ↓
                         Repository Layer (데이터 접근, JPA)
                                       ↓
                              MySQL Database
```

### 2.2 ALB 경로 기반 라우팅

**라우팅 규칙:**

| 우선순위 | Path Pattern | 대상 | Path Rewrite |
|---------|--------------|------|--------------|
| 1 | `/api/v1/*` | BE (Spring Boot, 8080) | `/api/v1/auth/login` → `/auth/login` |
| 2 | `/*` (default) | FE (Express.js, 3000) | 없음 |

**클라이언트 호출 흐름:**
```
1. Client: GET /api/v1/posts/123
2. ALB: Path match → /api/v1/* → BE Target Group
3. ALB: Path rewrite → /posts/123
4. BE: @GetMapping("/posts/{postId}") 처리
5. Response: 200 OK + PostResponse
```

**BE 내부 경로 (변경 없음):**
- `/auth/**` - 인증
- `/users/**` - 사용자
- `/posts/**` - 게시글, 댓글, 좋아요
- `/images/**` - 이미지
- `/stats` - 통계
- `/health` - 헬스체크

### 2.3 계층별 책임

**Controller:**
- DTO 검증: @Valid + Bean Validation (표준), Manual Validation (@RequestPart 예외)
- 요청 → Service 전달, 응답 포매팅
- 예외 메시지: 영어 통일 (ErrorCode 기본 메시지와 일관성)
- **권한**: GET(조회)는 public, POST/PATCH/DELETE(작성/수정/삭제)는 Authentication 필수
- 위치: `com.ktb.community.controller`

**Service:**
- 비즈니스 로직, 트랜잭션 관리
- 엔티티 ↔ DTO 변환
- 위치: `com.ktb.community.service`

**Repository:**
- CRUD, 커스텀 쿼리 (JPA)
- 위치: `com.ktb.community.repository`

---

## 3. 패키지 구조

**주요 패키지:**
- `config/` - SecurityConfig, JpaAuditingConfig, S3Config, RateLimit, RateLimitAspect
- `controller/` - AuthController, UserController, PostController, CommentController, ImageController
- `service/` - AuthService, UserService, PostService, CommentService, LikeService, ImageService
- `repository/` - UserRepository, PostRepository, CommentRepository, PostLikeRepository, ImageRepository, UserTokenRepository, PostStatsRepository
- `entity/` - User, Post, Comment, PostLike, Image, UserToken, PostStats, PostImage, PostImageId, BaseTimeEntity, BaseCreatedTimeEntity
- `dto/request/` - LoginRequest, SignupRequest, PostCreateRequest, CommentCreateRequest
- `dto/response/` - ApiResponse, UserResponse, PostResponse, CommentResponse
- `security/` - JwtTokenProvider, JwtAuthenticationFilter, CustomUserDetailsService
- `exception/` - GlobalExceptionHandler, BusinessException
- `util/` - PasswordValidator, FileValidator, S3KeyGenerator
- `enums/` - UserStatus, PostStatus, CommentStatus, UserRole

**상세 파일 구조:** 필요 시 IDE 탐색 또는 프로젝트 루트 참조

---

## 4. 데이터베이스 설계

**테이블 구조 및 DDL:** `@docs/be/DDL.md` 참조

**핵심 관계:**
- `users` 1:N → `posts`, `comments`, `post_likes`, `user_tokens`
- `posts` 1:1 → `post_stats`, 1:N → `comments`, M:N → `images` (via `post_images`)
- `post_likes` 복합 유니크 키: `(user_id, post_id)` 중복 방지

**주요 인덱스:**
- `users`: email, nickname (UNIQUE), user_status
- `posts`: created_at, (user_id, created_at)
- `comments`: (post_id, created_at, comment_id)
- `post_likes`: post_id, (user_id, post_id) UNIQUE

---

## 5. API 설계

**전체 API 스펙 및 엔드포인트 목록**: **@docs/be/API.md** 참조

### 공통 응답 구조
```json
{
  "message": "작업_결과_메시지",
  "data": { /* 응답 데이터 또는 null */ },
  "timestamp": "2025-10-01T14:30:00"
}
```

---

## 6. 인증 및 보안

### 6.1 JWT 토큰

**Access Token:** 15분, API 인증  
**Refresh Token:** 7일, Access 갱신, `user_tokens` 테이블 저장  
**Guest Token:** 5분, 회원가입용 임시 토큰 (role: GUEST, userId: 0)

**Payload 예시:**
```json
{
  "sub": "user_id",
  "email": "user@example.com",
  "role": "USER",  // USER, ADMIN, GUEST
  "iat": 1234567890,
  "exp": 1234569690
}
```

**Guest Token 용도:**
- 회원가입 시 프로필 이미지 업로드용 (Presigned URL 발급)
- userId: 0, role: GUEST로 식별
- Refresh Token 없음 (일회용)
- 미사용 이미지는 TTL 1시간 후 자동 삭제

### 6.2 인증 흐름 (Authorization Header + localStorage)

**토큰 전략:**
- **Access Token (AT)**: localStorage 저장 (MPA 환경) → Authorization 헤더로 전송
- **Refresh Token (RT)**: httpOnly Cookie → 자동 전송 (7일, user_tokens 테이블 저장)
- **Guest Token**: localStorage 저장 (회원가입 임시 토큰, 5분, 일회용)

**로그인 플로우:**
1. Client → POST /auth/login (credentials: 'include')
2. 서버 → BCrypt 검증
3. 서버 → Access + Refresh 토큰 생성
4. 서버 → **Response Body에 AT 반환** + **httpOnly Cookie로 RT 발급**
5. 서버 → RT를 user_tokens 테이블에 저장
6. 클라이언트 → `localStorage.setItem('accessToken', data.accessToken)` (MPA 환경)

**API 호출 플로우:**
1. Client → `Authorization: Bearer {localStorage.getItem('accessToken')}` 헤더 추가
2. JwtAuthenticationFilter → Authorization 헤더에서 AT 추출
3. JwtAuthenticationFilter → 토큰 검증 및 **Request Attribute에 userId 저장** (`req.setAttribute()`)
4. Controller → `@RequestAttribute Long userId`로 인증 정보 접근
5. 비즈니스 로직 실행

**토큰 갱신 플로우:**
1. Client → POST /auth/refresh_token (credentials: 'include')
2. 서버 → httpOnly Cookie에서 RT 추출
3. 서버 → user_tokens 테이블 검증
4. 서버 → 새 AT 발급 → **Response Body에 반환**
5. 클라이언트 → `localStorage.setItem('accessToken', newToken)` 업데이트

**보안 트레이드오프:**
- **AT in localStorage**: XSS 취약 (BUT: Express.js 서버 렌더링으로 XSS 벡터 최소화)
- **RT in httpOnly Cookie**: XSS 안전, CSRF 대응 (SameSite 설정)
- **MPA 환경**: JS 메모리 공유 불가 → localStorage 불가피

### 6.3 핵심 보안 설정 (최소 Spring Security + 커스텀 필터)

**설계 철학:**
- **Spring Security**: CORS 설정만 담당 (인증/인가 로직 없음)
- **JwtAuthenticationFilter**: 모든 인증 처리 (커스텀 Servlet Filter, `@Order(1)`)
- **장점**: 간단한 로직, Spring Security 복잡도 제거, 직접적인 제어

**SecurityConfig (최소 구성):**
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    // 다중 origin 지원 (개발 + EC2)
    List<String> allowedOrigins = new ArrayList<>();
    allowedOrigins.add(frontendUrl);  // 환경변수 (포트 포함)

    // 포트 제거 버전 추가 (iptables 포트 포워딩 대응)
    String baseUrl = frontendUrl.replaceAll(":\\d+$", "");
    if (!baseUrl.equals(frontendUrl)) {
        allowedOrigins.add(baseUrl);
    }

    // localhost 개발 환경 추가
    if (!frontendUrl.contains("localhost")) {
        allowedOrigins.add("http://localhost:3000");
        allowedOrigins.add("http://localhost");
    }

    config.setAllowedOrigins(allowedOrigins);
    config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);  // Cookie (RT) 전송 허용
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}

@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)  // CSRF 비활성화 (JWT 방식)
            .formLogin(AbstractHttpConfigurer::disable)  // 기본 로그인 폼 비활성화
            .httpBasic(AbstractHttpConfigurer::disable)  // HTTP Basic 인증 비활성화
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()  // Spring Security는 인증 안 함 (JwtAuthenticationFilter가 처리)
            );

    return http.build();
}
```

**JwtAuthenticationFilter (커스텀 Servlet Filter):**
```java
@Component
@Order(1)  // 가장 먼저 실행
public class JwtAuthenticationFilter implements Filter {

    private final JwtTokenProvider jwtTokenProvider;

    // 공개 엔드포인트 (인증 불필요)
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/auth/login", "/auth/logout", "/auth/refresh_token", "/auth/guest-token",
            "/users/signup", "/users", "/terms", "/privacy", "/stats", "/health"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String uri = req.getRequestURI();
        String method = req.getMethod();

        // 1. OPTIONS 요청은 통과 (CORS Preflight)
        if (HttpMethod.OPTIONS.matches(method)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 공개 엔드포인트는 선택적 인증 (JWT 있으면 검증, 없으면 통과)
        if (isPublicEndpoint(uri, method)) {
            String jwt = extractJwt(req);
            if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
                Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
                req.setAttribute("userId", userId);  // 로그인 상태 저장
            }
            chain.doFilter(request, response);
            return;
        }

        // 3. 보호된 엔드포인트 - JWT 필수
        String jwt = extractJwt(req);
        if (jwt == null || !jwtTokenProvider.validateToken(jwt)) {
            sendUnauthorized(response, "No access token");
            return;
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
        req.setAttribute("userId", userId);  // Request Attribute에 저장

        chain.doFilter(request, response);
    }

    // JWT 추출 (Authorization: Bearer {token})
    private String extractJwt(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    // 공개 엔드포인트 판단 (GET 요청, PUBLIC_PATHS, /images/*)
    private boolean isPublicEndpoint(String uri, String method) {
        if (PUBLIC_PATHS.contains(uri)) return true;
        if (uri.startsWith("/images/") && !uri.equals("/images/presigned-url")) return true;
        if (HttpMethod.GET.matches(method)) {
            if (uri.equals("/posts/users/me/likes")) return false;  // 예외: 인증 필요
            if (uri.startsWith("/posts") || uri.startsWith("/users/")) return true;
        }
        return false;
    }
}
```

**인증 처리 흐름:**
1. **JwtAuthenticationFilter** (`@Order(1)`) 실행
2. Authorization 헤더에서 JWT 추출
3. JwtTokenProvider로 토큰 검증 (서명, 만료 시간)
4. Payload에서 userId 추출 → `request.setAttribute("userId", userId)`
5. Controller에서 `@RequestAttribute Long userId`로 접근

**권한 제어 방식:**
- **Spring Security**: 없음 (`.anyRequest().permitAll()`)
- **JwtAuthenticationFilter**: 공개/보호 엔드포인트 구분
- **Controller**: `@RequestAttribute` 파라미터로 userId 체크
  - userId 없음 → 401 Unauthorized (Filter에서 차단)
  - userId 존재 → 인증된 사용자

**보안 요소:**
- **CORS**: Express.js 연동 (allowCredentials: true)
- **CSRF**: 비활성화 (JWT Authorization 헤더 방식, Cookie 기반 CSRF 불필요)
- **XSS**: AT는 localStorage (취약) BUT Express.js 서버 렌더링으로 XSS 벡터 최소화
- **Session**: STATELESS (세션 사용 안 함)

### 6.4 비밀번호 정책

**정책:**
- 길이: 8자 이상, 20자 이하
- 대문자: 최소 1개 (`[A-Z]`)
- 소문자: 최소 1개 (`[a-z]`)
- 특수문자: 최소 1개 (`[!@#$%^&*(),.?":{}|<>]`)

**구현:**
```java
public class PasswordValidator {
    private static final String REGEX =
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*(),.?\":{}|<>]).{8,20}$";

    public static boolean isValid(String password) {
        return password != null && password.matches(REGEX);
    }
}
```

### 6.5 Rate Limiting

**정책:**
- 제한: 엔드포인트별 개별 설정 (5~200회/분, Tier 전략)
- 키: FQCN.methodName + IP 주소 + 사용자 ID (인증 시)
- 저장소: 인메모리 (Caffeine Cache), 추후 Redis
- 응답: 429 Too Many Requests

**설계 결정사항:**

| 항목 | 선택 | 이유 |
|------|------|------|
| 알고리즘 | Token Bucket | Burst traffic 허용, 점진적 refill, 산업 표준 |
| 라이브러리 | Bucket4j | Redis 전환 용이, 경량, Spring 친화적 |
| 캐시 | Caffeine | 자동 만료 (10분), 메모리 상한 (10k), LRU 지원 |
| 키 형식 | FQCN.methodName:IP:userId | 패키지 다른 동일 클래스명 충돌 방지 |
| 엔드포인트 격리 | FQCN 포함 | RESTful CRUD 메서드명 중복 대응 |

**구현 (AOP):**
```java
@Aspect
@Component
public class RateLimitAspect {
    // Caffeine Cache: 10분 미사용 시 자동 삭제, 최대 10,000개
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .build();

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String clientKey = getClientKey(pjp);
        int requestsPerMinute = rateLimit.requestsPerMinute();

        // Token Bucket: 600ms마다 1개 토큰 refill
        Bucket bucket = buckets.get(clientKey, k -> createBucket(requestsPerMinute));

        if (!bucket.tryConsume(1)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return pjp.proceed();
    }

    // 키 예시: "com.ktb.community.controller.AuthController.login:192.168.1.1:123" (FQCN:IP:userId)
    private String getClientKey(ProceedingJoinPoint pjp) {
        String methodName = pjp.getSignature().getDeclaringTypeName()
                          + "." + pjp.getSignature().getName();
        // ... IP + userId 조합
        return methodName + ":" + userKey;
    }
}
```

**적용 대상 (3-Tier 전략):**

**Tier 1 (5회/분) - 강한 제한:**
- AuthController.login - brute-force 방지
- UserController.changePassword - enumeration 방지

**Tier 2 (10-50회/분) - 중간 제한:**
- UserController.signup (10회/분) - spam bot, 정상 사용자 재시도 고려
- AuthController.refreshToken (30회/분) - 비정상 토큰 갱신 감지
- UserController.updateProfile (30회/분) - 프로필 수정 spam 방지
- UserController.deactivateAccount (10회/분) - 계정 비활성화 남용 방지
- PostController.createPost (30회/분) - 게시글 spam 방지
- CommentController.createComment (50회/분) - 댓글 spam 방지 (더 빈번)
- ImageController.uploadImage (10회/분) - 파일 업로드 부하
- ImageController.getPresignedUrl (10회/분) - Presigned URL 발급 남용 방지

**Tier 3 (제한 없음 또는 200회/분) - 약한 제한/해제:**
- 모든 GET 조회 API - 제한 없음 (페이지네이션으로 제어)
- AuthController.logout - 제한 없음 (공격 동인 없음)
- PostController.likePost/unlikePost (200회/분) - 빈번한 액션, 원자적 UPDATE
- Post/Comment 수정 API (50회/분) - 본인 권한 검증 있음
- Post/Comment 삭제 API (30회/분) - Soft Delete

---

## 7. 주요 비즈니스 로직

### 7.1 게시글 작성 흐름

**핵심 구현 패턴:**
```java
@Transactional
public PostResponse createPost(PostCreateRequest request, Long userId) {
    // 1. 사용자 검증 (ACTIVE 필터링 - Soft Delete 정책)
    User user = userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "User not found or inactive with id: " + userId));

    // 2. 게시글 생성 및 저장
    Post post = request.toEntity(user);
    Post savedPost = postRepository.save(post);

    // 3. 통계 초기화 (Builder 기본값 0 사용)
    PostStats stats = PostStats.builder()
            .post(savedPost)
            .build();
    PostStats savedStats = postStatsRepository.save(stats);
    
    // 4. Post에 stats 연결 (필수 - PostResponse null 방지)
    savedPost.updateStats(savedStats);

    // 5. 이미지 TTL 해제 (imageId 있을 경우)
    if (request.getImageId() != null) {
        Image image = imageRepository.findById(request.getImageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND,
                        "Image not found with id: " + request.getImageId()));
        
        image.clearExpiresAt();  // expires_at → NULL (영구 보존)
        
        PostImage postImage = PostImage.builder()
                .post(savedPost)
                .image(image)
                .displayOrder(1)
                .build();
        postImageRepository.save(postImage);
    }

    return PostResponse.from(savedPost);
}
```

**설계 결정사항:**
- **예외**: BusinessException + ErrorCode 사용 (ResourceNotFoundException 아님)
- **통계 초기화**: Builder 기본값 의존 (명시적 0 설정 불필요)
- **updateStats() 필수**: 양방향 연관관계 동기화, PostResponse null 방지
- **이미지 TTL**: clearExpiresAt() 호출로 영구 보존 전환

**참조:** PostService.java:49-105

### 7.2 좋아요 처리 - 동시성 제어

**문제:** 동시 좋아요 시 Race Condition
**해결:** DB 레벨 원자적 UPDATE

```java
@Modifying(clearAutomatically = false)
@Query("UPDATE PostStats ps SET ps.likeCount = ps.likeCount + 1, " +
       "ps.lastUpdated = CURRENT_TIMESTAMP WHERE ps.postId = :postId")
int incrementLikeCount(@Param("postId") Long postId);
```

**적용:**
- `incrementLikeCount()` / `decrementLikeCount()`
- `incrementCommentCount()` / `decrementCommentCount()`
- `incrementViewCount()`

**선택 이유:** 낙관적 락(재시도 폭증), 비관적 락(과도) 대비 최적

**clearAutomatically = false 사용 이유 (Phase 5 최적화):**
- **detached entity 방지**: 영속성 컨텍스트와 독립적 동작, LazyInitializationException 해결
- **Optimistic Update 패턴**: 클라이언트가 UI에서 즉시 업데이트 (+1/-1), 서버는 stale 값 반환
- **성능 개선**: EntityManager.refresh() 호출 불필요 → DB 통신 17% 감소 (6번 → 5번)
- **동시성 보장**: 원자적 UPDATE 유지 (100% 데이터 정확도)
- **상세**: API.md Section 3.2 (조회수), Section 6.1/6.2 (좋아요), PLAN.md Phase 5

### 7.3 페이지네이션

**하이브리드 전략 (Phase 5)**: latest(cursor), likes(offset)

#### Cursor 기반 (latest 전용)
```java
// Repository
@Query("SELECT p FROM Post p JOIN FETCH p.user LEFT JOIN FETCH p.stats " +
       "WHERE p.postStatus = :status AND p.postId < :cursor " +
       "ORDER BY p.postId DESC")
List<Post> findByStatusWithCursor(PostStatus status, Long cursor, Pageable pageable);

// Service (limit+1 패턴)
List<Post> posts = (cursor == null)
    ? postRepository.findByStatusWithoutCursor(PostStatus.ACTIVE, PageRequest.of(0, limit + 1))
    : postRepository.findByStatusWithCursor(PostStatus.ACTIVE, cursor, PageRequest.of(0, limit + 1));

boolean hasMore = posts.size() > limit;
if (hasMore) posts.remove(limit);

Long nextCursor = hasMore && !posts.isEmpty()
    ? posts.get(posts.size() - 1).getPostId()
    : null;

// 응답: { posts, nextCursor, hasMore }
```

**장점:**
- 인덱스 활용 (postId PRIMARY KEY)
- 실시간 안정성 (중간 데이터 변경에 안전)
- 무한 스크롤 최적화

**단점:**
- 특정 페이지 번호 이동 불가
- total_count 제공 불가

#### Offset/Limit (likes 등)
```java
int page = offset / limit;
Pageable pageable = PageRequest.of(page, limit, getSort(sort));
Page<Post> postPage = postRepository.findByStatusWithUserAndStats(PostStatus.ACTIVE, pageable);

// 응답: { posts, pagination: { total_count } }
```

**장점:**
- 페이지 번호 네비게이션
- total_count 제공 (UX)

**단점:**
- Offset 클수록 성능 저하
- 실시간 데이터 변경에 취약

**현재 구현:**
- GET /posts?sort=latest → Cursor
- GET /posts?sort=likes → Offset
- GET /posts/{postId}/comments → Offset (변경 없음)

**향후 계획:**
- likes 정렬 cursor 전환 (복합 cursor: likeCount:postId)
- GET /posts/users/me/likes cursor 전환

### 7.4 댓글 작성 흐름

**핵심 구현 패턴:**
```java
@Transactional
public CommentResponse createComment(Long postId, CommentCreateRequest request, Long userId) {
    // 1. 게시글 존재 확인 (Fetch Join, ACTIVE만)
    Post post = postRepository.findByIdWithUserAndStats(postId, PostStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND,
                    "Post not found with id: " + postId));

    // 2. 사용자 확인 (ACTIVE 필터링 - Soft Delete 정책)
    User user = userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "User not found or inactive with id: " + userId));

    // 3. 댓글 생성 및 저장
    Comment comment = request.toEntity(post, user);
    Comment savedComment = commentRepository.save(comment);

    // 4. 댓글 수 자동 증가 (동시성 제어 - 원자적 UPDATE)
    postStatsRepository.incrementCommentCount(postId);

    return CommentResponse.from(savedComment);
}
```

**설계 결정사항:**
- **Repository 메서드**: findByIdWithUserAndStats (Fetch Join + ACTIVE 필터링)
- **동시성 제어**: incrementCommentCount() - DB 레벨 원자적 UPDATE (Section 12.3)
- **트랜잭션 경계**: 댓글 저장 + 통계 증가가 동일 트랜잭션 (원자성 보장)

**권한 검증 패턴 (수정/삭제):**
```java
// 작성자 본인만 수정 가능
if (!comment.getUser().getUserId().equals(userId)) {
    throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS,
            "Not authorized to update this comment");
}
```

**참조**: CommentService.java (전체 CRUD), **@docs/be/API.md Section 5**, **@docs/be/DDL.md** (comments 테이블)

---

### 7.5 이미지 업로드 전략

**5가지 패턴 비교:**

| 항목 | Multipart 직접 업로드 | 2단계 업로드 | Presigned URL | 이미지 제거 | Lambda 메타데이터 |
|------|---------------------|-------------|--------------|-----------|-----------------|
| **사용처** | 회원가입, 프로필 수정 | 게시글 작성/수정 | 클라이언트 직접 업로드 | 프로필 수정 | Lambda 이미지 처리 |
| **요청 횟수** | 1회 (multipart/form-data) | 2회 (POST /images → POST /posts) | 2회 (GET presigned-url → PUT S3) | 1회 (removeImage: true) | 2회 (Lambda → POST /images/metadata) |
| **트랜잭션** | 원자적 (이미지 포함) | 독립적 (이미지 선행) | 독립적 (이미지 선행) | 원자적 (TTL 복원) | 독립적 (S3 선행) |
| **UX 장점** | 간편함, 한 번에 완료 | 미리보기, 임시 저장 지원 | 서버 부하 감소, 대용량 지원 | 명시적 제거 의도 | 서버 부하 분산 |
| **핵심 메서드** | AuthService.signup() | PostService.createPost() | ImageService.generatePresignedUrl() | UserService.updateProfile() | ImageService.registerImageMetadata() |
| **엔드포인트** | POST /users/signup | POST /images | GET /images/presigned-url | PATCH /users/{id} | POST /images/metadata |

**핵심 구현 패턴:**

**패턴 1 - Multipart 직접 업로드 (AuthService):**
```java
@Transactional
public AuthResponse signup(SignupRequest request, MultipartFile profileImage) {
    // 1. 이메일 중복 확인
    if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
        throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, 
                "Email already exists: " + request.getEmail());
    }
    
    // 2. 닉네임 중복 확인
    if (userRepository.existsByNickname(request.getNickname())) {
        throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS, 
                "Nickname already exists: " + request.getNickname());
    }
    
    // 3. 비밀번호 정책 검증
    if (!PasswordValidator.isValid(request.getPassword())) {
        throw new BusinessException(ErrorCode.INVALID_PASSWORD_POLICY, 
                PasswordValidator.getPolicyDescription());
    }
    
    // 4. 비밀번호 암호화
    String encodedPassword = passwordEncoder.encode(request.getPassword());
    
    // 5. 프로필 이미지 업로드 (있을 경우)
    Image image = null;
    if (profileImage != null && !profileImage.isEmpty()) {
        ImageResponse imageResponse = imageService.uploadImage(profileImage);
        image = imageRepository.findById(imageResponse.getImageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        image.clearExpiresAt();  // TTL 해제 (영구 보존)
    }
    
    // 6. User 생성 (DTO 변환 사용)
    User user = request.toEntity(encodedPassword);
    if (image != null) {
        user.updateProfileImage(image);
    }
    userRepository.save(user);
    
    // 7. 자동 로그인 - 토큰 발급
    return generateTokens(user);
}
```

**패턴 2 - 2단계 업로드 (PostService):**
```java
@Transactional
public PostResponse createPost(PostCreateRequest request, Long userId) {
    // ... 게시글 생성 및 저장 ...
    
    // 이미지 연결 (imageId가 있을 경우)
    if (request.getImageId() != null) {
        Image image = imageRepository.findById(request.getImageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND,
                        "Image not found with id: " + request.getImageId()));
        
        image.clearExpiresAt();  // TTL 해제 (영구 보존)
        
        PostImage postImage = PostImage.builder()
                .post(savedPost)
                .image(image)
                .displayOrder(1)
                .build();
        postImageRepository.save(postImage);
    }
    
    return PostResponse.from(savedPost);
}
```

**패턴 3 - 이미지 제거 (UserService):**
```java
@Transactional
public UserResponse updateProfile(Long userId, Long authenticatedUserId, 
                                   UpdateProfileRequest request) {
    // ... 권한 확인 및 사용자 조회 ...
    
    // Case 1: 새 이미지로 교체 (profileImage: File) - 최우선
    if (request.getProfileImage() != null && !request.getProfileImage().isEmpty()) {
        // 1. 기존 이미지 TTL 복원 (고아 이미지 처리)
        Image oldImage = user.getProfileImage();
        if (oldImage != null) {
            oldImage.setExpiresAt(LocalDateTime.now().plusHours(1));
        }
        
        // 2. 새 이미지 업로드
        ImageResponse imageResponse = imageService.uploadImage(request.getProfileImage());
        Image newImage = imageRepository.findById(imageResponse.getImageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        
        // 3. 새 이미지 연결 (영구 보존)
        newImage.clearExpiresAt();
        user.updateProfileImage(newImage);
    }
    // Case 2: 이미지 제거 요청 (removeImage: true)
    else if (Boolean.TRUE.equals(request.getRemoveImage())) {
        Image oldImage = user.getProfileImage();
        if (oldImage != null) {
            // TTL 복원 (고아 이미지 처리)
            oldImage.setExpiresAt(LocalDateTime.now().plusHours(1));
            
            // 관계 해제
            user.updateProfileImage(null);
        }
    }
    // Case 3: 이미지 유지 (둘 다 없음)

    return UserResponse.from(user);
}
```

**패턴 4 - Presigned URL (ImageService):**
```java
@Transactional
public PresignedUrlResponse generatePresignedUrl(String filename, String contentType) {
    // 1. 확장자 검증 (.jpg, .jpeg, .png, .gif)
    String extension = getExtension(filename);
    validateImageExtension(extension);

    // 2. S3 Key 생성 (images/yyyy/MM/dd/{UUID}.ext)
    String s3Key = s3KeyGenerator.generate(extension);

    // 3. Content-Type 결정 (파라미터 또는 확장자 기반 추론)
    String resolvedContentType = contentType != null
        ? contentType
        : getContentTypeFromExtension(extension);

    // 4. Presigned URL 생성 (15분 유효)
    PutObjectRequest putObjectRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Key)
        .contentType(resolvedContentType)
        .build();

    PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
        .signatureDuration(Duration.ofMinutes(15))
        .putObjectRequest(putObjectRequest)
        .build();

    PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
    String uploadUrl = presignedRequest.url().toString();

    // 5. DB에 Image 레코드 사전 등록 (TTL 1시간)
    Image image = Image.builder()
        .imageUrl(getPublicUrl(s3Key))
        .s3Key(s3Key)
        .expiresAt(LocalDateTime.now().plusHours(1))
        .build();
    Image savedImage = imageRepository.save(image);

    // 6. PresignedUrlResponse 반환
    return PresignedUrlResponse.builder()
        .imageId(savedImage.getImageId())
        .uploadUrl(uploadUrl)
        .s3Key(s3Key)
        .expiresAt(presignedRequest.expiration())
        .build();
}
```

**Presigned URL 클라이언트 사용법:**
```javascript
// 1. Presigned URL 발급
const { imageId, uploadUrl } = await fetch('/images/presigned-url?filename=photo.jpg')
    .then(res => res.json()).then(r => r.data);

// 2. S3 직접 업로드 (PUT)
await fetch(uploadUrl, {
    method: 'PUT',
    body: file,  // File 객체
    headers: { 'Content-Type': file.type }
});

// 3. 회원가입/게시글 작성 시 imageId 사용
await fetch('/posts', {
    method: 'POST',
    body: JSON.stringify({ title, content, imageId })
});
```

**Presigned URL vs Multipart 비교:**
| 항목 | Presigned URL | Multipart (기존) |
|------|--------------|-----------------|
| **서버 부하** | 낮음 (S3 직접) | 높음 (서버 경유) |
| **대용량 파일** | 적합 (5GB까지) | 부적합 (서버 메모리) |
| **Progress** | 클라이언트 직접 추적 | XMLHttpRequest 필요 |
| **보안** | URL 만료 (15분) | 서버 검증 |
| **복잡도** | 2회 요청 | 1회 요청 |

**TTL 패턴 (공통 핵심):**
- **업로드 시**: ImageService가 `expires_at = NOW() + 1시간` 설정
- **사용 시**: `image.clearExpiresAt()` 호출 → `expires_at = NULL` (영구 보존)
- **제거 시**: `image.setExpiresAt(NOW() + 1시간)` 호출 → TTL 복원 (고아 처리)
- **미사용 시**: Phase 4 배치가 expires_at < NOW() 조건으로 S3 + DB 삭제
- **인덱스**: `idx_images_expires` 활용으로 빠른 조회

**설계 결정사항:**
- **검증 로직**: AuthService.signup()에서 이메일/닉네임/비밀번호 검증 모두 구현됨 (생략 아님)
- **User 생성**: Builder 직접 사용 대신 `request.toEntity()` + `updateProfileImage()` 패턴
- **TTL 복원 전략**: 
  - 패턴 1 (새 이미지 교체): 기존 이미지 TTL 1시간 복원 → 고아 처리
  - 패턴 3 (이미지 제거): 기존 이미지 TTL 1시간 복원 → 관계 해제
  - Phase 4 배치가 expires_at < NOW() 조건으로 S3 + DB 삭제
- **트랜잭션 안전성**: 패턴 1/3은 완전 원자적, 패턴 2/4는 이미지만 선행 업로드 (S3 파일 고아 가능)
- **Presigned URL 하이브리드**: 기존 Multipart 방식 유지 + Presigned URL 신규 제공
  - Multipart: 소규모 이미지, 서버 검증 필요 시
  - Presigned URL: 대용량 파일, 서버 부하 분산 필요 시

**참조**:
- AuthService.signup() - 패턴 1 전체 구현
- PostService.createPost() - 패턴 2 전체 구현
- UserService.updateProfile() - 패턴 3 전체 구현
- ImageService.generatePresignedUrl() - 패턴 4 전체 구현
- ImageService.uploadImage() - 공통 검증 로직
- **@docs/be/API.md Section 2.1, 2.3, 3.3, 4.1, 4.3**
- **@docs/be/DDL.md** (images 테이블)

---

## 8. 예외 처리

### 8.1 예외 처리 구조

**단일 통합 예외 아키텍처:**
- **ErrorCode enum**: 에러 정보 중앙 관리 (28개 에러 코드) - HTTP 상태, 에러 코드, 메시지 포함
- **BusinessException**: 단일 통합 예외 클래스 (ErrorCode 래핑, 4가지 생성자 지원)
- **GlobalExceptionHandler**: 중앙 예외 처리 (@RestControllerAdvice, 7개 핸들러)

**ErrorCode 형식:** `{DOMAIN}-{NUMBER}` (예: USER-001, POST-001, AUTH-001)

**전체 에러 코드 목록**: **@docs/be/API.md Section 7** (도메인별 28개 에러 코드)

---

### 8.2 예외 핸들러 목록

| 핸들러 | 예외 타입 | HTTP 상태 | 설명 |
|--------|-----------|-----------|------|
| handleBusinessException | BusinessException | ErrorCode 기반 | 비즈니스 로직 에러 (통합) |
| handleMaxUploadSizeExceeded | MaxUploadSizeExceededException | 413 | 파일 크기 초과 (Phase 3.5) |
| handleValidationException | MethodArgumentNotValidException | 400 | DTO 검증 실패 (@Valid) |
| handleIllegalArgumentException | IllegalArgumentException | 400 | 잘못된 요청 파라미터 |
| handleIllegalStateException | IllegalStateException | 400 | 비즈니스 로직 오류 |
| handleGeneralException | Exception | 500 | 예상하지 못한 서버 오류 |
| handleNullPointerException | NullPointerException | 500 | Null 참조 오류 |

---

### 8.3 핵심 패턴

**통합 예외 처리 (BusinessException):**
```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ApiResponse<ErrorDetails>> handleBusinessException(BusinessException ex) {
    ErrorCode errorCode = ex.getErrorCode();
    ErrorDetails errorDetails = ErrorDetails.of(ex.getMessage());
    ApiResponse<ErrorDetails> response = ApiResponse.error(errorCode.getCode(), errorDetails);
    
    return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
}
```

**핵심 장점:**
- ErrorCode enum이 HTTP 상태, 에러 코드, 메시지 모두 관리
- Service Layer에서 `throw new BusinessException(ErrorCode.XXX)` 한 줄로 통일
- GlobalExceptionHandler가 자동 매핑 (ErrorCode → HTTP 상태 + 응답)

**사용 예시:**
```java
// Service Layer
if (!comment.getUser().getUserId().equals(userId)) {
    throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS,
            "Not authorized to update this comment");
}

// 자동 변환: UNAUTHORIZED_ACCESS → HTTP 403 + "COMMON-XXX" + 메시지
```

**참조:** GlobalExceptionHandler.java (전체 핸들러 7개), ErrorCode.java (28개 에러 정의)

---

## 9. 데이터 변환 (Entity ↔ DTO)

**패턴:**
```java
// Entity → DTO
public static PostResponse from(Post post) {
    return PostResponse.builder()
        .postId(post.getPostId())
        .title(post.getTitle())
        .author(UserSummary.from(post.getUser()))
        .stats(PostStatsResponse.from(post.getStats()))
        .build();
}

// DTO → Entity
public Post toEntity(User user) {
    return Post.builder()
        .title(this.title)
        .content(this.content)
        .status(PostStatus.ACTIVE)
        .user(user)
        .build();
}
```

**DTO 검증:** `@NotBlank`, `@Size`, `@Valid` 활용

---

## 10. 설정 파일

**핵심 설정 항목:**

| 항목 | 설정값 | 설명                            |
|------|--------|-------------------------------|
| **HikariCP** | Spring Boot default | DB 커넥션 풀 (default: 20)         |
| **Multipart** | max-file-size: 5MB, max-request-size: 10MB | 이미지 업로드 제한                    |
| **JPA** | ddl-auto: validate, open-in-view: false, default_batch_fetch_size: 100 | 운영 모드, OSIV 비활성화, N+1 최적화     |
| **JWT** | access: 15분 (900000ms), refresh: 7일 (604800000ms) | 토큰 유효기간                       |
| **S3** | bucket/region 환경 변수 주입 | DefaultCredentialsProvider 체인 |
| **Rate Limit** | 코드 기반 (@RateLimit 어노테이션) | 엔드포인트별 개별 설정                  |
| **Logging** | root: INFO, com.ktb.community: DEBUG | 개발 환경 로깅 레벨                   |
| **Server** | port: 8080 | 서버 포트                         |

**환경 변수 (필수):**
```bash
# Phase 1-2 (Database & JWT)
DB_URL=jdbc:mysql://localhost:3306/community
DB_USERNAME=root
DB_PASSWORD=<MySQL 비밀번호>
JWT_SECRET=<256bit 이상 시크릿>

# Phase 3.5+ (S3)
AWS_ACCESS_KEY_ID=<AWS Access Key>
AWS_SECRET_ACCESS_KEY=<AWS Secret Key>
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=<버킷 이름>
```

**참조:** `src/main/resources/application.yaml` (전체 설정)

---

## 11. 테스트 전략

**커버리지 목표:** Service 80%+, Repository 60%+, 전체 60%+

**패턴:**
```java
// Service 테스트
@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock private PostRepository postRepository;
    @InjectMocks private PostService postService;
    
    @Test
    void createPost_Success() {
        // Given, When, Then + verify()
    }
}

// Repository 테스트
@DataJpaTest
class PostRepositoryTest {
    @Autowired private PostRepository postRepository;
    // 실제 DB 쿼리 테스트
}
```

---

## 12. 성능 최적화

### 12.1 데이터베이스 최적화

**N+1 문제 해결:**
```java
@Query("SELECT p FROM Post p " +
       "JOIN FETCH p.user " +
       "LEFT JOIN FETCH p.stats " +
       "WHERE p.status = :status")
Page<Post> findByStatusWithUserAndStats(...);
```

**인덱스 활용:**
- DDL.md의 인덱스 정의 준수
- EXPLAIN으로 쿼리 실행 계획 분석

**Batch Fetch Size 설정:**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100  # N+1 최적화
```
- to-many lazy loading 시 IN 쿼리로 일괄 로드
- Post 목록 조회: 11개 쿼리 → 2개 쿼리 (82% 감소)
- 코드 변경 없이 설정만으로 적용 가능

**User Soft Delete 필터링:**
```java
// Repository: Spring Data 파생 메서드
Optional<User> findByUserIdAndUserStatus(Long userId, UserStatus userStatus);

// Service: ACTIVE 필터 적용
User user = userRepository.findByUserIdAndUserStatus(userId, UserStatus.ACTIVE)
    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
        "User not found or inactive with id: " + userId));
```
- INACTIVE/DELETED 사용자는 게시글 작성/댓글 작성/좋아요 불가
- 보안 취약점 제거: 탈퇴 사용자의 활동 차단
- 적용 범위: UserService 4곳, PostService/CommentService/LikeService 각 1곳

### 12.2 캐싱 전략 (추후)

**Redis 도입 시:**
- 토큰 관리: Refresh Token 저장
- 세션 관리: 사용자 세션 캐싱
- 데이터 캐싱: 자주 조회되는 게시글 목록
- TTL 설정: 데이터 특성별 만료 시간

### 12.3 동시성 제어

**문제:** PostStats (좋아요/댓글/조회수) 동시 업데이트 시 Race Condition

**해결:** DB 레벨 원자적 UPDATE

```sql
UPDATE post_stats
SET like_count = like_count + 1,
    last_updated = NOW()
WHERE post_id = ?;
```

**Repository 구현:**
```java
@Modifying(clearAutomatically = false)
@Query("UPDATE PostStats ps SET ps.likeCount = ps.likeCount + 1, " +
       "ps.lastUpdated = CURRENT_TIMESTAMP WHERE ps.postId = :postId")
int incrementLikeCount(@Param("postId") Long postId);

@Modifying(clearAutomatically = false)
@Query("UPDATE PostStats ps SET ps.likeCount = ps.likeCount - 1, " +
       "ps.lastUpdated = CURRENT_TIMESTAMP " +
       "WHERE ps.postId = :postId AND ps.likeCount > 0")
int decrementLikeCount(@Param("postId") Long postId);
```

**적용 대상:**
- `likeCount`: 좋아요 증감
- `commentCount`: 댓글 수 증감
- `viewCount`: 조회수 증가 (가장 빈번)

**대안 검토:**
- ❌ 낙관적 락 (@Version): 재시도 폭증
- ❌ 비관적 락 (FOR UPDATE): 과도한 오버헤드
- ✅ 원자적 UPDATE: 성능과 일관성 최적

**Phase 5 최적화 (clearAutomatically = false):**
- **문제**: PostStats 조회 후 JPQL UPDATE 실행 시 영속성 컨텍스트의 stale 데이터 유지
- **기존 방식**: clearAutomatically = true → 1차 캐시 자동 초기화, 이후 조회 시 DB 재조회 필요
- **최적화**: clearAutomatically = false → 1차 캐시 유지, Optimistic Update 패턴으로 클라이언트가 보정
- **결과**: DB 통신 17% 감소, detached entity 이슈 해결, 동시성 보장 유지
- **Trade-off**: 서버 응답값은 stale (증가 전 값), 클라이언트가 UI에서 +1/-1 처리
- **참조**: PLAN.md Phase 5 (Line 295-306), API.md Section 3.2/6.1/6.2

---

## 13. 배포 및 운영

**배포 문서:** `@docs/deployment/` 폴더 참조 (상세 가이드)

**주요 문서:**
- **@docs/deployment/README.md**: 배포 전체 가이드 (마스터 인덱스)
- **@docs/deployment/CI-CD.md**: CI/CD 파이프라인 (GitHub Actions → Jenkins)
- **@docs/deployment/EC2-DEPENDENCIES.md**: 수동 배포 의존성 (Java, Docker, MySQL, EFS, systemd)

**환경 변수:** `@docs/deployment/EC2-DEPENDENCIES.md Section "환경 변수"` 참조 (7개 필수 변수)
- DB_URL, DB_USERNAME, DB_PASSWORD
- JWT_SECRET
- AWS_S3_BUCKET, AWS_REGION
- FRONTEND_URL

**배치 작업:**
- 고아 이미지 정리: 매일 새벽 3시, @Scheduled (ImageCleanupBatchService)
- TTL 만료 이미지 (expires_at < NOW) 자동 삭제 (S3 + DB)

**인프라 구성:** `@docs/deployment/CI-CD.md Section "인프라 아키텍처"` 참조
- ALB (경로 기반 라우팅: `/api/v1/*` → BE)
- EC2 (Private Subnet, Auto Scaling 대응)
- RDS MySQL 8.0+
- S3 (이미지 저장)
- ECR (Docker 이미지 저장소)

**로그 레벨:** `@docs/be/LLD.md Section 14` 참조
- 운영: INFO, 개발: DEBUG
- 주요 포인트: API 요청/응답, 인증 실패, 비즈니스 에러

---

## 14. 로깅 정책

### 14.1 로그 레벨 기준

| 레벨 | 용도 | 예시 |
|------|------|------|
| debug | 상세 디버깅 (개발 환경만) | 메서드 진입/종료, 상태 변화 |
| info | 비즈니스 이벤트 (비개인정보) | 배치 시작/종료, 통계 |
| warn | 의도적 실패 (복구 가능) | 404, 403, 409, RateLimit |
| error | 예상치 못한 실패 | 500, 예외 스택 |

### 14.2 민감정보 처리 규칙

**금지:**
- ❌ Email, UserId (개인 식별자)
- ❌ 비밀번호, 토큰 원본

**허용:**
- ✅ PostId, CommentId, ImageId (콘텐츠/리소스 식별자)
- ✅ HTTP 메서드, 경로

**대안:**
- 요청 추적: MDC + requestId (Phase 6+)
- 감사 로그: 별도 AuditLog 테이블

### 14.3 현재 적용 현황

**Service Layer:**
- 성공 플로우: debug (운영 환경에서 숨김)
- 비즈니스 이벤트: info (배치, 통계만)

**GlobalExceptionHandler:**
- 의도적 실패: warn (4xx, RateLimit)
- 예상치 못한 실패: error (5xx, 예외)

**운영 환경 설정**: `application-prod.yaml`에서 INFO 레벨 사용

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|-----------|
| 2025-09-30 | 1.0 | 초기 LLD 작성 |
| 2025-10-04 | 1.1 | Claude Code 최적화 (참조 기반, 섹션 재구조화) |
| 2025-10-04 | 1.2 | 핵심 섹션 완전 복원 (6.4, 6.5, 12.3) |
| 2025-10-04 | 1.3 | Section 7.4 댓글 작성 흐름 추가 (참조 무결성 복원) |
| 2025-10-10 | 1.4 | HTML 이스케이프 코드 수정 (Section 6.5) |
| 2025-10-15 | 1.5 | Section 14 로깅 정책 추가, Service Layer 로그 레벨 조정 |
| 2025-10-15 | 1.6 | Section 12.1 User Soft Delete 필터링 및 Batch Fetch Size 추가 |
| 2025-10-21 | 1.7 | Section 6.3 SecurityConfig CSRF 설정 업데이트 (API 엔드포인트 제외 반영) |
| 2025-10-22 | 1.8 | 중복 제거 및 참조 최적화 (Section 5 API 엔드포인트, Section 8.1 에러 코드 - API.md 참조) |
| 2025-10-22 | 1.9 | Section 7.2, 12.3 clearAutomatically 파라미터 동기화 (true → false, Phase 5 최적화 반영) |
| 2025-11-03 | 2.0 | Section 7.5 Pattern 3 추가 (이미지 제거, UserService.updateProfile), TTL 복원 전략 문서화 |
| 2025-12-01 | 2.1 | Section 2.1-2.2 ALB 경로 기반 라우팅 추가, 프로젝트명 DC2로 변경, 문서 손상 복구 |
| 2025-12-01 | 2.2 | Section 7.5 패턴 4 Presigned URL 추가, 하이브리드 업로드 전략 문서화 |