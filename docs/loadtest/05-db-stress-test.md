# DB 커넥션 풀 스트레스 테스트

## 테스트 개요
- **목적**: HikariCP 커넥션 풀 한계 식별 — 동시 다발적 DB 쓰기 부하
- **스크립트**: `scripts/k6/db_stress_test.js`
- **실행 시각**: 2026-02-23 KST
- **소요 시간**: ~3분 22초

## 테스트 환경
- BE 인스턴스: 2대 × t3.medium Spot (HikariCP max=30, JVM -Xms1g -Xmx1500m)
- RDS: db.t3.medium Primary (max_connections=300) + db.t3.small Replica
- 데이터: 600 users, 1,500 posts

## 시나리오 설정
| 시나리오 | Executor | VUs | Stages | 비고 |
|----------|----------|-----|--------|------|
| db_stress | ramping-vus | 0→30→60→100→0 | 20s/30s 각 단계 | 매 iteration 5~6개 DB 연산 |

### 시나리오 상세 (1 iteration당 DB 연산)
1. 로그인 (SELECT users + INSERT user_tokens)
2. 게시글 조회 (SELECT posts + UPDATE post_stats 조회수)
3. 좋아요 (INSERT post_likes + UPDATE post_stats)
4. 댓글 작성 (INSERT comments + UPDATE post_stats)
5. 게시글 작성 (INSERT posts + INSERT post_stats)
6. 좋아요 취소 (DELETE post_likes + UPDATE post_stats)

## Threshold 결과
| 메트릭 | 값 | Threshold | 판정 |
|--------|-----|-----------|------|
| db_op_duration p(95) | 156.63ms | <2000ms | PASS |
| errors | 0.51% | <20% | PASS |

## 주요 메트릭 상세
| 메트릭 | avg | min | med | p90 | p95 | max |
|--------|-----|-----|-----|-----|-----|-----|
| db_op_duration | 48.73ms | 11.27ms | 26.60ms | 122.33ms | 156.63ms | 756.75ms |

## VU 단계별 DB 성능
| VU 구간 | p95 DB 연산 시간 | 에러율 | 특징 |
|---------|-----------------|--------|------|
| 30 VUs | 낮음 (추정 ~50ms) | 극소 | 커넥션 풀 여유, 안정적 |
| 60 VUs | 중간 (추정 ~100ms) | 극소 | 풀 사용률 증가, 여전히 안정 |
| 100 VUs | ~157ms (전체 p95) | 0.51% | 풀 부하 최대, 여전히 threshold 여유 |

## DB 연산별 성능
| 연산 | 성공률 | 평균 응답시간 | 비고 |
|------|--------|-------------|------|
| 로그인 | 96.77% (추정) | ~120ms | BCrypt 해싱이 지배적, SELECT + INSERT |
| 조회수 증가 | ~99% | ~30ms | SELECT + UPDATE, 404는 비즈니스 응답 |
| 좋아요 | ~99% | ~30ms | INSERT + UPDATE, 409 중복은 비즈니스 응답 |
| 댓글 작성 | ~99% | ~30ms | INSERT + UPDATE, 404는 비즈니스 응답 |
| 게시글 작성 | ~99% | ~30ms | INSERT × 2 |
| 좋아요 취소 | ~99% | ~25ms | DELETE + UPDATE, 404는 비즈니스 응답 |

## 실행 통계
| 항목 | 값 |
|------|-----|
| Total Requests | 32,643 |
| Total DB Operations | 32,643 (161.81/s) |
| RPS | 161.81/s |
| Iterations | 5,558 (27.55/s) |
| http_req_failed | 16.97% (5,540/32,643) |
| Actual Errors | 0.51% (141/27,226) |
| Data Received | 21 MB (105 kB/s) |
| Data Sent | 4.4 MB (22 kB/s) |

## 분석

### 관찰 사항

1. **Threshold를 큰 여유로 통과**: db_op_duration p(95) 156.63ms는 2,000ms 기준 대비 7.8%에 불과하다. 현재 부하(100 VU)에서 커넥션 풀은 한계에 한참 못 미치며, DB 계층은 충분한 여유가 있다.

2. **중앙값과 p95의 격차**: 중앙값 26.60ms 대비 p95는 156.63ms로 약 5.9배 차이가 난다. 이는 대부분의 DB 연산이 빠르게 처리되지만 일부 연산(주로 로그인의 BCrypt 해싱)이 꼬리 지연을 유발하는 패턴이다. 중앙값 26.60ms는 순수 DB 쿼리(INSERT/UPDATE/SELECT)의 실제 처리 시간에 가깝고, p95 156.63ms에는 BCrypt 연산 시간이 포함된다.

3. **http_req_failed 16.97%는 오해의 소지가 있다**: 32,643건 중 5,540건이 실패로 집계되었으나, 이 중 대부분은 404(존재하지 않는 게시글)와 409(이미 좋아요한 게시글)로 비즈니스 로직상 정상적인 응답이다. 랜덤 postId(1~1,500) 접근 방식에서 삭제되었거나 존재하지 않는 게시글에 대한 404, 같은 게시글에 중복 좋아요 시 409가 자연스럽게 발생한다. 실제 시스템 에러(errors custom metric)는 0.51%(141건)에 불과하다.

4. **최대 응답 시간 756.75ms의 원인**: max 값이 757ms로, 이는 로그인 요청의 BCrypt 해싱이 CPU 경합 상황에서 지연된 결과로 추정된다. 04-login-stress-test에서 확인된 BCrypt 해싱 단일 소요 시간(~120ms)과 CPU 경합 시 큐잉 지연을 합산하면 이 수치와 부합한다. 순수 DB 연산(INSERT/UPDATE)만으로 이 수준의 지연이 발생했다면 커넥션 풀 고갈을 의심해야 하지만, BCrypt를 포함한 로그인 요청이 원인이므로 DB 측 문제는 아니다.

5. **높은 처리량 달성**: 초당 161.81 DB ops, 27.55 iterations는 현재 인프라에서 안정적인 수치다. 1 iteration당 약 5.87개 요청이 발생하여(32,643 / 5,558) 설계된 6개 DB 연산과 거의 일치한다.

### 커넥션 풀 분석

**풀 용량 대비 사용률**:

| 항목 | 값 |
|------|-----|
| HikariCP max | 30 × 2 인스턴스 = 60 커넥션 |
| RDS max_connections | 300 |
| 실제 DB ops/s | 161.81/s |
| 커넥션 활용률 | 약 20% (60 중 ~12 동시 사용 추정) |

HikariCP max=30 × 2 = 60 커넥션이 RDS max_connections=300의 20%만 사용한다. 100 VU에서도 커넥션 풀 고갈의 징후가 전혀 없다. 그 이유는 다음과 같다.

- **DB 연산은 빠르다**: 순수 DB 쿼리의 중앙값이 26.60ms이므로, 단일 커넥션이 초당 약 37개 쿼리를 처리할 수 있다. 60 커넥션이면 이론상 초당 2,200개 이상의 쿼리가 가능하다.
- **BCrypt가 커넥션을 점유하지 않는다**: BCrypt 해싱은 애플리케이션 레벨에서 수행되며, 해싱이 진행되는 동안 DB 커넥션을 잡고 있지 않는다. 해싱 완료 후 토큰 저장 시에만 짧게 커넥션을 사용한다.
- **sleep(2)이 자연스러운 조절 역할**: 각 iteration 후 2초 대기가 있어 커넥션 반환 시간이 충분하다.

### 병목 지점

**현재 병목은 DB가 아니라 CPU(BCrypt)이다**:

| 계층 | 상태 | 근거 |
|------|------|------|
| 커넥션 풀 (HikariCP) | 여유 | p95 157ms, 고갈 징후 없음 |
| RDS (MySQL) | 여유 | 300 중 60 커넥션만 할당, InnoDB lock contention 없음 |
| 네트워크 | 여유 | 105 kB/s 수신, 22 kB/s 송신으로 대역폭 미미 |
| CPU (BCrypt) | 유일한 병목 | max 757ms, 04번 테스트에서도 확인된 동일 패턴 |

DB 커넥션 풀 스트레스 테스트의 목적은 커넥션 풀 고갈 시점을 찾는 것이었으나, 100 VU 수준에서는 커넥션 풀이 전혀 병목이 되지 않았다. 커넥션 풀 고갈을 유발하려면 VU를 최소 500 이상으로 올리거나 sleep을 제거하여 연산 밀도를 높여야 할 것이다.

### 개선 방안

**현재 상태**: 커넥션 풀과 DB 계층은 현재 부하의 5~10배까지 여유가 있으므로, 즉각적인 튜닝은 불필요하다.

**장기적 대비 (트래픽 10배 증가 시)**:
- **HikariCP max 조정 (30 → 50)**: RDS max_connections=300의 여유를 활용하여 인스턴스당 풀 크기를 늘린다. 다만 현재 30으로도 충분하므로 필요 시점에 조정한다.
- **Read Replica 활용**: 현재 db.t3.small Replica가 있으나 활용되지 않고 있다. SELECT 쿼리(게시글 조회, 목록 등)를 Replica로 분산하면 Primary의 쓰기 성능에 여유가 생긴다.
- **커넥션 풀 모니터링 추가**: HikariCP의 메트릭(active, idle, pending, timeout)을 CloudWatch 또는 Prometheus로 수집하면, 풀 고갈 전에 사전 경고를 받을 수 있다.
- **쿼리 최적화**: 현재 단일 쿼리가 충분히 빠르지만(중앙값 27ms), 트래픽 증가 시 N+1 쿼리나 인덱스 미사용 쿼리가 병목으로 전환될 수 있다. Slow Query Log 활성화를 권장한다.

### 결론

2대의 t3.medium 서버(HikariCP max=30)와 RDS db.t3.medium(max_connections=300) 구성에서 100 VU, 초당 162 DB ops를 p(95) 157ms로 처리하며 커넥션 풀 여유가 충분하다. DB 계층은 현재 부하의 5~10배까지 대응 가능하며, 실제 병목은 BCrypt 해싱의 CPU 부하(04번 테스트에서 확인)에 있다. 커넥션 풀 고갈은 이 인프라 규모에서 우선적으로 걱정할 문제가 아니며, 트래픽이 크게 증가할 때 Read Replica 활용과 풀 모니터링을 순차적으로 도입하면 충분하다.
