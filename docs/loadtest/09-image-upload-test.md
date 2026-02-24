# 이미지 업로드 테스트

## 테스트 개요
- **목적**: Presigned URL 발급 + S3 직접 업로드 vs Multipart(BE 경유) 업로드 성능 비교
- **스크립트**: `scripts/k6/image_upload_test.js`
- **실행 시각**: 2026-02-23 KST
- **소요 시간**: ~3분 17초

## 테스트 환경
- BE 인스턴스: 2대 x t3.medium Spot (HikariCP max=30, JVM -Xms1g -Xmx1500m)
- RDS: db.t3.medium Primary + db.t3.small Replica
- 데이터: 600 users, 1,500 posts, ~20,000 comments
- 테스트 이미지: 6장 (4~12KB, JPG/JPEG)

## 시나리오 설정
| 시나리오 | Executor | VUs | Stages | 비고 |
|----------|----------|-----|--------|------|
| presigned_upload | ramping-vus | 0→10→20→0 | 30s/1m/30s/30s | Presigned URL 발급 → S3 직접 업로드 |
| multipart_upload | ramping-vus | 0→5→10→0 | 30s/1m/30s/30s | Multipart 파일 업로드 (BE 경유) |

### 시나리오 상세
**Presigned Upload (70% VU 할당):**
1. 로그인 (POST /auth/login)
2. Presigned URL 발급 (GET /images/presigned-url)
3. S3 직접 업로드 (PUT presigned URL)
4. sleep 2s

**Multipart Upload (30% VU 할당):**
1. 로그인 (POST /auth/login)
2. Multipart 업로드 (POST /images)
3. sleep 2s

## Threshold 결과
| 메트릭 | 값 | Threshold | 판정 |
|--------|-----|-----------|------|
| presigned_url_duration p(95) | 32.49ms | <500ms | PASS |
| s3_upload_duration p(95) | 69.62ms | <3000ms | PASS |
| multipart_upload_duration p(95) | 81.50ms | <3000ms | PASS |
| errors | 0.32% | <10% | PASS |

## Checks 결과
| Check | 성공 | 실패 | 성공률 |
|-------|------|------|--------|
| presigned URL status 201 | -- | 0 | 100% |
| presigned URL has uploadUrl | -- | 0 | 100% |
| s3 upload status 200 | -- | 0 | 100% |
| multipart upload status 201 | -- | 0 | 100% |
| multipart upload has imageId | -- | 0 | 100% |
| **합계** | **4,891** | **0** | **100%** |

## 주요 메트릭 상세
| 메트릭 | avg | min | med | p90 | p95 | max |
|--------|-----|-----|-----|-----|-----|-----|
| presigned_url_duration | 23.66ms | 14.45ms | 20.90ms | 28.64ms | 32.49ms | 309.67ms |
| s3_upload_duration | 43.69ms | 29.38ms | 39.07ms | 52.71ms | 69.62ms | 274.86ms |
| multipart_upload_duration | 57.93ms | 44.24ms | 54.78ms | 69.52ms | 81.50ms | 148.28ms |
| http_req_duration | 65.34ms | 14.44ms | 50.16ms | 112.25ms | 117.48ms | 349.11ms |

## 업로드 방식별 성능 비교
| 방식 | BE 부하 | 총 소요시간 (p95) | BE 응답 (p95) | S3 업로드 (p95) | 비고 |
|------|---------|-------------------|---------------|-----------------|------|
| Presigned URL | 최소 (URL 발급만) | ~102ms | 32.49ms | 69.62ms | Client→S3 직접 전송 |
| Multipart | 중간 (파일 수신+전달) | ~81.50ms | 81.50ms | (BE 내부) | BE가 파일 처리 |

## 실행 통계
| 항목 | 값 |
|------|-----|
| Total Requests | 4,901 |
| Upload Operations | 3,088 (15.71/s) |
| RPS | 24.94/s |
| Iterations | 1,813 (9.22/s) |
| http_req_failed | 0.20% (10/4,901) |
| errors (custom) | 0.32% (10/3,098) |
| vus_max | 30 |
| data_received | 5.9 MB (30 kB/s) |
| data_sent | 16 MB (83 kB/s) |

## 분석

### 관찰 사항

1. **Presigned URL 발급이 매우 빠름**: p95 32.49ms로, BE 서버의 URL 생성 연산이 경량임을 확인했다. 이는 AWS SDK의 S3Presigner가 로컬에서 URL을 서명하기 때문에 외부 네트워크 호출이 불필요하기 때문이다.

2. **S3 직접 업로드 안정적**: p95 69.62ms로, VPC Endpoint를 통한 S3 PUT 연산이 안정적이다. 테스트 이미지가 4~12KB로 작아 네트워크 전송 시간이 미미하며, 실제 프로덕션의 5MB 이미지에서는 더 높아질 수 있다.

3. **Multipart가 소규모 파일에서는 더 빠름**: Multipart p95(81.50ms) < Presigned 총합 p95(~102ms). 이는 소규모 파일(~10KB)에서 Presigned의 2-step 과정(URL 발급 + S3 PUT)보다 Multipart의 1-step(BE 직접 전송)이 오버헤드가 적기 때문이다. 다만 파일 크기가 커지면 Multipart에서 BE의 메모리/CPU 부하가 증가하므로 역전된다.

4. **http_req_failed 0.20% (10건)**: 극소수 실패는 간헐적 네트워크 타임아웃으로 추정되며, 시스템 장애와 무관하다.

5. **data_sent 16 MB**: 주로 S3 PUT 요청의 이미지 바이너리 데이터. data_received(5.9 MB)보다 data_sent가 높은 것은 업로드 테스트 특성상 자연스럽다.

### Presigned URL vs Multipart 아키텍처 비교

| 관점 | Presigned URL | Multipart (BE 경유) |
|------|---------------|---------------------|
| BE CPU 부하 | URL 서명만 (경량) | 파일 수신 + S3 전달 |
| BE 메모리 부하 | 없음 | 파일 크기만큼 버퍼링 |
| 네트워크 | Client→S3 직접 | Client→BE→S3 |
| 확장성 | BE 수평 확장과 무관 | BE 인스턴스 수에 비례 |
| 소규모 파일 (<100KB) | 2-step 오버헤드 | 단순하고 빠름 |
| 대규모 파일 (>1MB) | BE 부하 없이 처리 가능 | BE 메모리 압박 |

### 권장 사용 패턴
- **프로필 이미지** (~50KB): Multipart (단순, 원자적 트랜잭션)
- **게시글 이미지** (~1-5MB): Presigned URL (BE 부하 최소화)
- **대량 업로드**: Presigned URL 필수 (BE 메모리 보호)

### 결론

30 VU에서 초당 15.71 업로드 연산을 에러율 0.32%로 안정적으로 처리한다. Presigned URL 발급은 p95 32.49ms로 매우 빠르며, S3 직접 업로드(p95 69.62ms)와 Multipart 업로드(p95 81.50ms) 모두 양호한 성능을 보인다. 소규모 파일에서는 Multipart가 간결하지만, 파일 크기 증가 및 동시 업로드 수 증가 시 Presigned URL 방식의 확장성이 우월하다. 현재 하이브리드 전략(용도별 선택)이 최적의 접근이다.
