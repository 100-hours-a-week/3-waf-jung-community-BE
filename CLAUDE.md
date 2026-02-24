# CLAUDE.md
This file provides guidance to Claude Code when working with this repository.

## 문서 구조

> ⚠️ **요약본**: 컨텍스트에 자동 로드 (빠른 맥락 파악)
@docs/be/PLAN-SUMMARY.md
@docs/be/PRD-SUMMARY.md
@docs/be/LLD-SUMMARY.md
@docs/be/API-SUMMARY.md
@docs/be/DDL-SUMMARY.md

> ⚠️ **원본**: 구현 전 반드시 Read 도구로 읽기

`docs/be/PLAN.md`
`docs/be/PRD.md`
`docs/be/LLD.md`
`docs/be/DDL.md`
`docs/be/API.md`

---

## ⚠️ 문서 참조 규칙 (필독)

### 워크플로우
1. **요약본 확인** → 위 테이블의 요약본이 컨텍스트에 자동 로드됨
2. **원본 읽기** → 구현 전 반드시 `Read docs/be/XXX.md` 실행
3. **구현 진행**
4. **원본 재참조** → 필요 시

### 언제 원본을 읽어야 하나?
| 작업 | 원본 참조 필수 |
|------|---------------|
| API 구현/수정 | `docs/be/API.md` Section N |
| DB 스키마 변경 | `docs/be/DDL.md` |
| 설계 패턴 적용 | `docs/be/LLD.md` Section N |
| FR 코드 확인 | `docs/be/PRD.md` FR-XXX |
| Phase 체크리스트 | `docs/be/PLAN.md` |

---


## Project Overview

**DC2 Community Platform** - Spring Boot 3.5.6 community platform
**Stack:** Java 24, MySQL 8.0+, JPA/Hibernate
**Package:** `com.ktb.community`


## 개발 워크플로우

### 1. 작업 시작 전
```bash
# 요약본으로 맥락 파악 (자동 로드됨)
# → 현재 Phase, FR 코드, 설계 결정 확인

# 관련 원본 읽기 (필수!)
Read docs/be/PLAN.md   # Phase 체크리스트
Read docs/be/API.md    # API 상세 스펙
Read docs/be/LLD.md    # 구현 패턴
```

### 2. 개발 중
```bash
# 필요한 원본만 선택적으로 읽기
Read docs/be/DDL.md    # DB 스키마
Read docs/be/API.md    # API 스펙

# 실제 코드
src/main/java/com/ktb/community/
```

### 3. 커밋 전

**커밋 메시지 형식 (Conventional Commits)**:
```bash
type: 간결한 제목 (한 줄, 50-80자)

[선택] Body (큰 기능만)
- 주요 변경사항 나열
- 계층별/기능별 그룹화
```

**Type별 규칙**:
- **작은 변경** (fix, docs, refactor) → **한 줄만**
- **큰 기능** (feat) → **제목 + Body**

**실제 예시**:
```bash
# 작은 변경 (한 줄)
fix: P0 Hotfix - Manual Validation 예외 메시지 영어 통일
docs: Phase 3.6 완료 상태로 업데이트
refactor: ErrorCode를 enums 패키지로 이동

# 큰 기능 (제목 + Body)
feat: Phase 3 게시글/댓글/좋아요 기능 구현

- Controller 계층 (2개)
  - PostController: 게시글 CRUD (6 endpoints)
  - CommentController: 댓글 CRUD (4 endpoints)

- Service 계층 (3개)
  - PostService: 게시글 관리, 조회수 자동 증가
  ...

- 단위 테스트 (30개, 100% 통과)
```

**추가 규칙**:
- FR 코드 포함 권장: `feat: FR-POST-001 게시글 작성 API 구현`
- Phase 진행률 업데이트: docs/be/PLAN.md 체크박스 수정
- Co-Authored-By 미사용

---

## 핵심 개발 원칙

**3-Layer Architecture 엄수:**
- Controller: DTO 검증, 요청/응답 처리
- Service: 비즈니스 로직, @Transactional
- Repository: 데이터 접근, JPA

**코드 통일성 패턴:**
- Entity ↔ DTO: `from()`, `toEntity()` 메서드
- 예외: CustomException 계층 사용
- 응답: ApiResponse 표준 구조

**성능 체크리스트:**
- [ ] N+1 방지 (FETCH JOIN)
- [ ] 페이지네이션
    - **latest 정렬**: Cursor (nextCursor/hasMore), offset 미지원 ⚠️
    - **likes 정렬**: Offset/Limit (total_count)
    - **댓글 목록**: Offset/Limit (total_count)
- [ ] 동시성 제어 (원자적 UPDATE: 좋아요/조회수/댓글수)

**테스트:**
- Phase별 단위 테스트 필수 (Service Layer 80%+)
- Phase 완료 전 모든 테스트 통과 확인

---

## 제약사항 (설계 배경)

**기술:**
- 토큰: RDB 저장 (user_tokens 테이블) → 추후 Redis
- 이미지: S3 이용, 서버에서 업로드 및 고아 이미지 처리

**데이터:**
- Soft Delete: User, Post, Comment (status 변경)
- Hard Delete: UserToken (배치)

**현재 가정:**
- 초기 트래픽 낮음 → 단일 서버 충분
- Phase 완료 후 고도화 (PLAN.md Phase 6+)

---

## Essential Commands

```bash
# Build & Run
./gradlew bootRun
./gradlew build

# Test
./gradlew test
./gradlew test --tests ClassName.methodName

# Database
mysql -u root -p community  # MySQL 접속
```

**Database:**
- URL: jdbc:mysql://localhost:3306/community
- DDL mode: update (자동 스키마 수정)
- 환경 변수: DB_PASSWORD, JWT_SECRET

---

## 빠른 참조

> 💡 요약본으로 맥락 파악 → 원본으로 상세 확인

| 필요한 정보 | 원본 (Read 필요) |
|------------|-----------------|
| 현재 Phase 확인 | `docs/be/PLAN.md` |
| FR 코드 찾기 | `docs/be/PRD.md` FR-XXX |
| 구현 패턴 | `docs/be/LLD.md` Section 7 |
| 테이블 구조 | `docs/be/DDL.md` |
| API 엔드포인트 | `docs/be/API.md` |
| 비밀번호 정책 | `docs/be/LLD.md` Section 6.4 |
| Rate Limiting | `docs/be/LLD.md` Section 6.5 |
| 동시성 제어 | `docs/be/LLD.md` Section 7.2, 12.3 |
| 이미지 업로드 | `docs/be/LLD.md` Section 7.5 |
| 예외 처리 | `docs/be/LLD.md` Section 8 |

---
