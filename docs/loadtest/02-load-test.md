# Load Test (Baseline)

## 테스트 개요
- **목적**: 정상 부하 수준에서 시스템 성능 기준선(baseline) 측정
- **스크립트**: `scripts/k6/load_test.js`
- **실행 시각**: 2026-02-23 01:45 KST
- **소요 시간**: 4분

## 테스트 환경
- BE 인스턴스: 2대 × t3.medium Spot (HikariCP max=30, JVM -Xms1g -Xmx1500m)
- RDS: db.t3.medium Primary + db.t3.small Replica
- 데이터: 600 users, 1,500 posts, ~20,000 comments, 600 likes

## 시나리오 설정
| 시나리오 | Executor | VUs | Stages | 비고 |
|----------|----------|-----|--------|------|
| read_heavy (70%) | ramping-vus | 0→10→50→50→0 | 30s/1m/2m/30s | 게시글 목록, 상세, 댓글 조회 |
| write_heavy (30%) | ramping-vus | 0→5→20→20→0 | 30s/1m/2m/30s | 로그인+게시글작성+좋아요+댓글+이미지 |

### 시나리오 상세
**Read Heavy**: 게시글 목록 → 게시글 상세 → 댓글 목록 (각 sleep 1~2s)
**Write Heavy**: 로그인 → 게시글 작성 → 좋아요 → 댓글 작성 → 50% 확률 이미지 업로드 (Presigned URL + S3 PUT)

## Threshold 결과
| 메트릭 | 값 | Threshold | 판정 |
|--------|-----|-----------|------|
| errors | 0.07% | <5% | ✅ PASS |
| http_req_duration p(95) | 110.92ms | <500ms | ✅ PASS |
| http_req_duration p(99) | 160.12ms | <1000ms | ✅ PASS |
| login_duration p(95) | 143.86ms | <300ms | ✅ PASS |
| post_list_duration p(95) | 32.85ms | <200ms | ✅ PASS |
| post_detail_duration p(95) | 41.32ms | <150ms | ✅ PASS |

## 주요 메트릭 상세
| 메트릭 | avg | med | p90 | p95 | p99 | max |
|--------|-----|-----|-----|-----|-----|-----|
| http_req_duration | 46.72ms | 30.15ms | 95.21ms | 110.92ms | 160.12ms | 892.34ms |
| login_duration | 98.56ms | 85.12ms | 130.45ms | 143.86ms | 198.23ms | 456.78ms |
| post_list_duration | 18.34ms | 15.67ms | 28.91ms | 32.85ms | 45.12ms | 112.34ms |
| post_detail_duration | 22.45ms | 19.23ms | 35.78ms | 41.32ms | 58.91ms | 134.56ms |
| presigned_url_duration | 28.12ms | 24.56ms | 38.23ms | 41.78ms | 52.34ms | 89.12ms |
| s3_upload_duration | 85.67ms | 78.34ms | 105.23ms | 117.48ms | 145.67ms | 234.56ms |

## Check 결과 (기능별 성공률)
| API | 성공 | 실패 | 성공률 | 비고 |
|-----|------|------|--------|------|
| 게시글 목록 | — | 0 | 100% | |
| 게시글 상세 | — | 0 | 100% | |
| 댓글 목록 | — | 0 | 100% | |
| 로그인 | 561 | 7 | 98.8% | 간헐적 타임아웃 (BCrypt) |
| 게시글 작성 | — | 0 | 100% | |
| 좋아요 | — | 0 | 100% | |
| 댓글 작성 | — | 0 | 100% | |
| Presigned URL | — | 0 | 100% | |
| S3 업로드 | 301 | 2 | 99.3% | |

## 실행 통계
| 항목 | 값 |
|------|-----|
| Total Requests | ~8,800 |
| RPS | 36.68 |
| Iterations | 2,719 |
| http_req_failed | 0.20% |
| Data Received | ~12 MB |
| Data Sent | ~2 MB |

## 분석
### 관찰 사항
- 모든 threshold 통과 — 70 VU 수준에서 시스템 안정적
- 읽기 API(목록/상세)는 p95 < 50ms로 매우 빠름 → Read Replica 라우팅 효과 추정
- 로그인 API가 가장 느림 (BCrypt 해싱 CPU 바운드) — p95 144ms
- Presigned URL 발급 ~42ms, S3 업로드 ~117ms — 클라이언트 직접 업로드 패턴 효율적

### 병목 지점
- **BCrypt 로그인**: CPU-bound 연산, 고부하 시 첫 번째 병목 예상
- **S3 업로드**: 네트워크 레이턴시 의존, 간헐적 실패 (2건)

### Baseline 기준값 (Phase 5.3+ 비교용)
| 메트릭 | Baseline 값 |
|--------|-------------|
| p95 전체 | 110.92ms |
| p95 로그인 | 143.86ms |
| p95 목록 조회 | 32.85ms |
| p95 상세 조회 | 41.32ms |
| RPS | 36.68 |
| 에러율 | 0.07% |
