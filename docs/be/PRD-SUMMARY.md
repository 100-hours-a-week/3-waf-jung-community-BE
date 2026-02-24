---
name: prd-summary
description: FR/NFR 코드 요약. 기능 구현 전 요구사항 코드 빠른 파악용.
---

# 요구사항 요약

## FR 코드 맵 (기능 요구사항)

| 도메인 | FR 코드 | 주요 기능 | 우선순위 |
|--------|---------|----------|----------|
| AUTH | FR-AUTH-001~004 | 회원가입, 로그인, 로그아웃, 토큰갱신 | P0 |
| USER | FR-USER-001~004 | 조회, 수정, 비밀번호변경, 탈퇴 | P0 |
| POST | FR-POST-001~005 | 작성, 목록, 상세, 수정, 삭제 | P0 |
| COMMENT | FR-COMMENT-001~004 | 작성, 목록, 수정, 삭제 | P0 |
| LIKE | FR-LIKE-001~003 | 좋아요, 취소, 목록 | P0 |
| IMAGE | FR-IMAGE-001~003 | 정보저장, 관리, 업로드 | P0 |

## NFR 코드 맵 (비기능 요구사항)

| 도메인 | 코드 | 주요 항목 |
|--------|------|----------|
| Security | NFR-SEC-001~004 | JWT(AT 15분/RT 7일), 비밀번호(BCrypt), API보안, Rate Limit |
| Performance | NFR-PERF-001~003 | 응답시간(<500ms), DB최적화(N+1), 페이지네이션 |
| Scalability | NFR-SCALE-001~002 | 3-Layer 아키텍처, 캐싱(Redis 추후) |
| Reliability | NFR-REL-001~003 | 예외처리, 로깅, 데이터 무결성 |
| Maintainability | NFR-MAINT-001~003 | 코드품질, 테스트(60%+), 문서화 |

## 핵심 검증 규칙

| 항목 | 규칙 |
|------|------|
| 이메일 | 유니크, 소문자 변환 |
| 닉네임 | 유니크, 10자 이내 |
| 비밀번호 | 8-20자, 대/소/특수문자 각 1개+ |
| 이미지 | JPG/PNG/GIF, 최대 5MB |

→ **상세 요구사항**: `docs/be/PRD.md` FR-XXX-NNN 검색
