# Smoke Test

## 테스트 개요
- **목적**: 배포 후 기본 동작 확인 (health, 게시글, 로그인, 통계)
- **스크립트**: `scripts/k6/smoke_test.js`
- **실행 시각**: 2026-02-23 01:30 KST
- **소요 시간**: 30초

## 테스트 환경
- BE 인스턴스: 2대 × t3.medium Spot (HikariCP max=30, JVM -Xms1g -Xmx1500m)
- RDS: db.t3.medium Primary + db.t3.small Replica
- 데이터: 600 users, 1,500 posts, ~20,000 comments, 600 likes

## 시나리오 설정
| 시나리오 | Executor | VUs | Duration | 비고 |
|----------|----------|-----|----------|------|
| default | constant-vus | 1 | 30s | 순차 API 호출 5종 |

### 호출 순서 (1 iteration)
1. `GET /health` → health check
2. `GET /posts?limit=5` → 게시글 목록
3. `GET /posts/1` → 게시글 상세
4. `POST /auth/login` → 로그인
5. `GET /stats` → 플랫폼 통계

## Threshold 결과
| 메트릭 | 값 | Threshold | 판정 |
|--------|-----|-----------|------|
| http_req_failed | 0.00% | <1% | ✅ PASS |
| http_req_duration p(95) | 183.55ms | <500ms | ✅ PASS |

## 주요 메트릭 상세
| 메트릭 | avg | med | p90 | p95 | p99 | max |
|--------|-----|-----|-----|-----|-----|-----|
| http_req_duration | 77.08ms | 33.76ms | 167.93ms | 183.55ms | 191.37ms | 195.22ms |

## Check 결과
| Check | 성공 | 실패 | 성공률 |
|-------|------|------|--------|
| health check ok | 5 | 0 | 100% |
| posts list ok | 5 | 0 | 100% |
| post detail ok | 5 | 0 | 100% |
| login ok | 5 | 0 | 100% |
| stats ok | 5 | 0 | 100% |
| **합계** | **25** | **0** | **100%** |

## 실행 통계
| 항목 | 값 |
|------|-----|
| Total Requests | 25 |
| RPS | 0.78 |
| Iterations | 5 |
| Data Received | ~15 KB |
| Data Sent | ~3 KB |

## 분석
### 관찰 사항
- 모든 API 엔드포인트 정상 동작 확인
- 평균 응답시간 77ms, 최대 195ms — 단일 사용자 기준 매우 양호
- health check 경로(`/api/v1/health`)와 ALB context-path stripping 정상 작동

### 결론
- 기본 기능 검증 통과 — 부하테스트 진행 가능
- BCrypt 로그인이 가장 느린 API (p95 ~184ms)로, 고부하 시 병목 가능성 예상
