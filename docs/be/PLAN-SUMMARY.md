---
name: plan-summary
description: Phase별 진행 현황 요약. 현재 Phase와 다음 할일 빠른 파악용.
---

# Phase 현황 요약

- **현재**: 프로젝트 종료
- **완료**: Phase 1-4, 3.5, 3.6, 3.7 (기반, 인증, 게시글/댓글/좋아요, 이미지, 통계, Presigned URL, CI/CD), Phase 5 (테스트/문서화)
- **대기**: Phase 6 (Redis, 조건부)

## 전체 로드맵

| Phase | 목표 | 상태      |
|-------|------|---------|
| Phase 1 | 기반 설정 (Entity, DB) | ✅ 완료    |
| Phase 2 | 인증/사용자 (JWT, CRUD) | ✅ 완료    |
| Phase 3 | 게시글/댓글/좋아요 | ✅ 완료    |
| Phase 3.5 | S3 이미지 업로드 | ✅ 완료    |
| Phase 3.6 | Multipart 전환 | ✅ 완료    |
| Phase 3.7 | Presigned URL 추가 | ✅ 완료    |
| Phase 4 | 통계/배치/CI-CD 파이프라인 | ✅ 완료    |
| Phase 5 | 테스트/문서화 | 프로젝트 종료 |
| Phase 6 | Redis 도입 | 프로젝트 종료 |

## Phase 3.7 핵심 체크리스트 (Presigned URL)

**문서화 (✅ 완료):**
- [x] API.md Section 4.3 - 엔드포인트 스펙
- [x] LLD.md Section 7.5 - 패턴 4 상세
- [x] 요약본 동기화

**구현 (✅ 완료):**
- [x] S3Config - S3Presigner Bean
- [x] PresignedUrlResponse DTO
- [x] ImageService.generatePresignedUrl()
- [x] ImageController.getPresignedUrl()
- [x] JwtAuthenticationFilter 인증 설정

**테스트 (✅ 완료):**
- [x] ImageService 단위 테스트 (7개, 100% 통과)
- [x] 확장자 검증 테스트
- [x] Rate Limit 테스트

## Phase 4 핵심 체크리스트 (통계/배치/CI-CD)

**통계 & 배치 (✅ 완료):**
- [x] /stats API 구현 (플랫폼 통계)
- [x] 고아 이미지 배치 스케줄러
- [x] TTL 기반 자동 정리 (DB + S3)

**CI/CD 파이프라인 (✅ 완료):**
- [x] GitHub Actions (Test, Build, ECR Push, Jenkins Trigger)
- [x] AWS ECR OIDC 인증 + 단일 리포지토리 전략
- [x] Jenkins Pipeline (Target Group 동적 조회, SSM 로드)
- [x] ALB 경로 기반 라우팅 (/api/v1/* → Backend)

**문서화 (✅ 완료):**
- [x] docs/deployment/CI-CD.md 작성
- [x] docs/deployment/README.md 업데이트
- [x] docs/be/LLD.md Section 2 (ALB 아키텍처)

## Phase 5 핵심 체크리스트

- [x] detached entity 문제 해결
- [x] Optimistic Update 패턴 도입
- [x] Cursor 페이지네이션 (latest)
- [ ] 전체 Service Layer 테스트 (80%+)
- [ ] Repository Layer 테스트 (60%+)
- [ ] API.md 최종 검토
- [ ] Postman Collection 작성

## 보류된 작업

- ImageCleanupBatchService S3 객체 삭제 버그

→ **상세 체크리스트/FR 매핑**: `docs/be/PLAN.md` 참조
