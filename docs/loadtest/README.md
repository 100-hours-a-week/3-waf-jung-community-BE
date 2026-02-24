# DC2 Community Platform — 부하테스트 결과

## 테스트 환경
| 항목 | 값 |
|------|-----|
| 인프라 | AWS ap-northeast-2, Terraform IaC |
| BE | 2× t3.medium Spot (ASG 1~4), Spring Boot 3.5.6, Java 24 |
| FE | 2× t3.micro Spot (고정), React + Express.js |
| DB | RDS MySQL 8.0 — Primary db.t3.medium + Replica db.t3.small |
| DB 설정 | max_connections=300, HikariCP max=30/instance |
| 데이터 | 600 users, 1,500 posts, 20,000+ comments, 600 likes |
| 네트워크 | VPC Endpoints (NAT Gateway 없음) |
| 테스트 도구 | K6 (로컬 Mac → HTTPS ALB) |
| 날짜 | 2026-02-23 |

## 트래픽 목표
| 지표 | 값 |
|------|-----|
| MAU | 1,000,000 |
| Avg RPS | ~307 |
| Peak RPS | ~480 |

## 결과 요약
| # | 테스트 | VUs | 시간 | p95 | 에러율 | RPS | 판정 |
|---|--------|-----|------|-----|--------|-----|------|
| 1 | [Smoke Test](./01-smoke-test.md) | 1 | 30s | 184ms | 0% | 0.78 | ✅ |
| 2 | [Load Test](./02-load-test.md) | 70 | 4m | 111ms | 0.07% | 36.7 | ✅ |
| 3 | [CCU Test](./03-ccu-test.md) | 10→200 | 7m | 137ms | 0.05% | 53.9 | ✅ |
| 4 | [Login Stress](./04-login-stress-test.md) | 30→100 | 3m | 413ms | 3.23% | 58.0 | ✅ |
| 5 | [DB Stress](./05-db-stress-test.md) | 30→100 | 3m | 157ms | 0.51% | 161.8 | ✅ |
| 6 | [Stress Test](./06-stress-test.md) | 50→300 | 10m | 142ms | 0.00% | 329.9 | ✅ |
| 7 | [Spike Test](./07-spike-test.md) | 10→200 | 2m | 119ms | 0.00% | 138.1 | ✅ |
| 8 | [Write Heavy](./08-write-heavy-test.md) | 20→50 | 4m | 108ms | 0.07% | 74.9 | ✅ |
| 9 | [Image Upload](./09-image-upload-test.md) | 10→30 | 3m | 117ms | 0.32% | 24.9 | ✅ |
| 10 | [Multi-Instance](./10-multi-instance.md) | varies | ~25m | — | — | — | ✅ |
| 11 | [Soak + 무중단배포](./11-soak-test.md) | 50 | 30m | 110ms | 0.04% | 34.7 | ✅ |

## 테스트 진행 순서

### Phase 5.1: Baseline (✅ 완료)
- Smoke Test → Load Test
- 기본 기능 검증 + 정상 부하 기준선 측정

### Phase 5.2: Capacity Discovery (✅ 완료)
- CCU Test → Login Stress → DB Stress
- 동시접속 한계, 인증 병목, 커넥션 풀 한계 식별

### Phase 5.3: Breaking Point (✅ 완료)
- Stress Test → Spike Test → Write Heavy → Image Upload
- 시스템 한계점, 급증 트래픽 복구, 쓰기 성능, 이미지 업로드

### Phase 5.4: Multi-Instance 비교 (✅ 완료)
- 1대 vs 2대 수평 확장 효과 측정
- 읽기 80% 워크로드에서 성능 차이 7% 이내

### Phase 5.5: Soak + 무중단 배포 (✅ 완료)
- 30분 지속 부하 + Instance Refresh zero-downtime 검증
- Instance Refresh 7분 소요, 에러율 0.04%

## 핵심 발견

### 성능
- **300 VU / 330 RPS에서 breaking point 미도달** — 현재 인프라(2× t3.medium)의 한계를 초과하지 않음
- 모든 읽기 API p95 < 50ms — DB 쿼리 최적화 양호
- Presigned URL 발급 p95 32ms — BE 서버 부하 최소화

### 병목
- **BCrypt 해싱이 유일한 실질적 병목** — 로그인 p95 413ms (100 VU에서 임계점 근접)
- DB 커넥션 풀: 100 VU에서도 20% 활용률, 여유 충분
- InnoDB lock contention: 동시 쓰기에서도 미관측

### 확장성
- 단일 인스턴스로 327 RPS 처리 가능 (2대와 7% 차이)
- **1M MAU avg RPS 307 목표의 107% 달성** (2대 기준)
- Peak 480 RPS는 3~4대로 충족 가능 (ASG auto-scaling)
- ASG auto-scaling 실제 동작 확인 (1→4대 자동 확장)

### 안정성
- 30분 Soak Test에서 메모리 누수, GC 압박 없음
- Instance Refresh 중 zero-downtime 달성 (에러율 0.04%)
- 200 VU spike에서 즉시 복구, 잔여 영향 없음

## Baseline 기준값 (Load Test)
| 메트릭 | 값 |
|--------|-----|
| p95 전체 | 110.92ms |
| p95 로그인 | 143.86ms |
| p95 목록 조회 | 32.85ms |
| p95 상세 조회 | 41.32ms |
| p95 Presigned URL | 41.78ms |
| p95 S3 업로드 | 117.48ms |
| RPS | 36.68 |
| 에러율 | 0.07% |

## 실행 가이드
```bash
# 인프라 올리기
cd infra/terraform/environments/loadtest
terraform apply

# K6 테스트 실행
k6 run -e BASE_URL=https://community.ktb-waf.cloud/api/v1 scripts/k6/{test_name}.js

# 인프라 정리
terraform destroy
```

## 참고 문서
- [인프라 구축 설명서](./INFRA-SETUP.md) — 아키텍처, IaC, 네트워크, 트러블슈팅
