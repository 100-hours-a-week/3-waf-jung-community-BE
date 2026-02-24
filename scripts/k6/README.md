# k6 부하테스트 스크립트

KTB Community 서비스 부하테스트용 k6 스크립트 모음

## 스크립트 목록

| 파일 | 용도 | VUs | 소요시간 |
|------|------|-----|----------|
| `smoke_test.js` | 배포 후 기본 동작 확인 | 1 | 30초 |
| `load_test.js` | 일반 부하 테스트 (읽기/쓰기 혼합) | 최대 70 | 4분 |
| `ccu_test.js` | CCU 단계별 성능 측정 (10→50→100→200) | 최대 200 | 7분 |

## 사전 요구사항

### k6 설치
```bash
# macOS
brew install k6

# Ubuntu/Debian
sudo apt-get install k6

# Docker
docker pull grafana/k6
```

### 테스트 환경
- DB에 더미 데이터가 삽입되어 있어야 함 (users 1200명, posts 3000개)
- 더미 데이터 비밀번호: `test1234` (bcrypt 해시)

## 실행 방법

### 로컬 테스트
```bash
# Smoke Test (기본 동작 확인)
k6 run smoke_test.js

# Load Test (부하 테스트)
k6 run load_test.js

# CCU Test (동시접속자 단계별 테스트)
k6 run ccu_test.js
```

### 원격 서버 테스트
```bash
# 환경변수로 BASE_URL 지정
k6 run -e BASE_URL=http://<SERVER_IP>:8080 smoke_test.js
k6 run -e BASE_URL=http://<SERVER_IP>:8080 ccu_test.js
```

### Docker로 실행
```bash
docker run --rm -i grafana/k6 run -e BASE_URL=http://host.docker.internal:8080 - < smoke_test.js
```

## 주요 메트릭

### 측정 항목
- `http_req_duration`: 요청 응답 시간
- `login_duration`: 로그인 API 응답 시간
- `post_list_duration`: 게시글 목록 조회 응답 시간
- `post_detail_duration`: 게시글 상세 조회 응답 시간
- `errors`: 에러율

### 성공 기준 (Thresholds)
| 메트릭 | 기준 |
|--------|------|
| p(95) 응답시간 | < 500ms |
| p(99) 응답시간 | < 1000ms |
| 에러율 | < 5% |

## CCU 테스트 단계

```
Stage 1: CCU 10   (0:00 ~ 1:30)
Stage 2: CCU 50   (1:30 ~ 3:00)
Stage 3: CCU 100  (3:00 ~ 4:30)
Stage 4: CCU 200  (4:30 ~ 6:00)
Ramp-down         (6:00 ~ 6:30)
```

## 결과 분석

### 출력 예시
```
     ✓ posts list ok
     ✓ post detail ok
     ✓ login ok

     checks.........................: 100.00% ✓ 1234  ✗ 0
     http_req_duration..............: avg=45ms min=12ms med=38ms max=234ms p(90)=89ms p(95)=112ms
     http_reqs......................: 5678    94.63/s
     errors.........................: 0.00%   ✓ 0     ✗ 5678
```

### 권장 분석 항목
1. **p(95) 응답시간**: 500ms 이내 권장
2. **에러율**: 5% 미만 권장
3. **RPS (Requests Per Second)**: CCU 대비 적정 처리량 확인
4. **VU당 반복 횟수**: iteration_duration 확인

## 문제 해결

### 로그인 실패
- 더미 데이터 비밀번호 해시 확인
- JWT_SECRET 환경변수 확인

### Connection refused
- 서버 실행 상태 확인
- 방화벽/보안그룹 설정 확인

### 높은 에러율
- DB 연결 풀 설정 확인
- 서버 리소스 (CPU/Memory) 모니터링


docker stop be && docker rm be

  docker run -d --name be \
    -p 8080:8080 \
    --restart unless-stopped \
    -e TZ=Asia/Seoul \
    -e JAVA_OPTS="-Xms512m -Xmx3g -XX:+UseG1GC" \
    -e DB_URL="jdbc:mysql://52.78.191.171:3306/community?serverTimezone=UTC" \
    -e DB_USERNAME=root \
    -e DB_PASSWORD=testpassword123 \
    -e JWT_SECRET="s9ezbmA9y9V3yexRRz1FM0Ty1Rta8XbS" \
    -e FRONTEND_URL="http://13.125.205.101:3000" \
    -e SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30 \
    -e SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=10 \
    --log-opt max-size=100m \
    wafriend1031/ktb-community-be:latest