# EC2 배포 가이드

DC2 Community Spring Boot 백엔드를 AWS EC2에 배포하기 위한 완벽한 가이드입니다.

## 문서 구조

이 가이드는 3개의 문서로 구성되어 있으며, 각 문서는 독립적으로 사용할 수 있습니다.

### 1. **README.md** (이 문서)
**Master Index + Common Reference**

**포함 내용:**
- 기술 스택 및 버전 정보 (공통 참조)
- 필수 환경 변수 (전체 목록)
- 시스템 요구사항 (배포 크기 추정)
- 계층별 의존성 다이어그램
- 배포 시나리오 개요 (A/B/C)
- 빠른 시작 가이드
- 보안 가이드
- 모니터링 및 트러블슈팅

**대상:** 모든 사용자 (첫 번째로 읽을 문서)

---

### 2. **EC2-DEPENDENCIES.md**
**수동 배포 가이드** - EC2에 직접 배포하기 위한 완전한 의존성 목록

**포함 내용:**
- Java 21 JDK 설치 (Amazon Corretto vs OpenJDK)
- MySQL 8.0+ 설정 (로컬 vs RDS)
- Gradle 8.14.3 빌드
- 시스템 라이브러리 (gcc, git, curl 등)
- 선택적 도구 (Nginx, Docker, systemd 등)
- 설치 순서 (권장 9단계)
- 트러블슈팅 및 보안 체크리스트

**대상:** 로컬 개발, 수동 배포

**참고:** 공통 정보(기술 스택, 환경 변수)는 README.md 참조

---

### 3. **CI-CD.md**
**자동 배포 가이드** - GitHub Actions + Jenkins를 통한 프로덕션 CI/CD 파이프라인

**포함 내용:**
- GitHub Actions 워크플로우 (Test, Build, ECR Push)
- Jenkins 파이프라인 (SSM Parameter 로드, SSH 배포)
- AWS ECR OIDC 인증
- Jenkins 서버 설치 및 설정
- ALB Target Group 기반 동적 배포
- 배포 플로우 및 롤백 절차
- 트러블슈팅 가이드

**대상:** 프로덕션 자동 배포

**참고:** 수동 배포는 EC2-DEPENDENCIES.md 참조

---

## 기술 스택 및 버전 정보

### 핵심 기술 스택

| 컴포넌트 | 버전 | 출처 |
|---------|------|------|
| Java | 21 (LTS) | build.gradle:13 |
| Spring Boot | 3.5.6 | build.gradle:3 |
| Gradle | 8.14.3 | gradle-wrapper.properties |
| MySQL | 8.0+ | application.yaml:9 |
| AWS SDK | 2.20.0 | build.gradle:61 |

### 런타임 라이브러리

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| JJWT | 0.12.3 | JWT 토큰 생성/검증 |
| Bucket4j | 8.10.1 | Rate Limiting (Token Bucket) |
| Caffeine | 3.1.8 | 로컬 캐시 |
| Spring Security | 3.5.6 | 인증/인가, BCrypt |
| HikariCP | (자동) | 커넥션 풀 |

---

## 필수 환경 변수

**7개 필수 변수:**
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET` (256bit 이상)
- `AWS_S3_BUCKET`, `AWS_REGION`
- `FRONTEND_URL`

**상세 설정 방법**: @docs/deployment/EC2-DEPENDENCIES.md Section "환경 변수 (공통 정보)"

**빠른 참조:**
```bash
# JWT Secret 생성
openssl rand -base64 32

# .env 파일 생성
chmod 600 .env
```

---

## 계층별 의존성 다이어그램

```
┌─────────────────────────────────────────────┐
│  Application Layer                          │
│  KTB Community Spring Boot 3.5.6           │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  Runtime Layer                              │
│  Java 21 + Spring Boot + Gradle 8.14       │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  Data/External Services                     │
│  MySQL 8.0+ | AWS S3 | Redis (Phase 6)    │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  Infrastructure (Optional)                  │
│  systemd | Nginx | Docker | CloudWatch    │
└─────────────────────────────────────────────┘
```

---

## 빠른 시작 (5분)

### 최소 설정으로 배포

```bash
# 1. EC2 인스턴스 생성 (Amazon Linux 2, t3.micro 이상)
#    보안 그룹: 22(SSH), 8080(API) 허용

# 2. SSH 접속
ssh -i your-key.pem ec2-user@your-instance-ip

# 3. 필수 도구 설치
sudo yum update -y
sudo yum install -y java-21-amazon-corretto-devel git

# 4. 프로젝트 클론 및 빌드
git clone https://github.com/<your-org>/community.git
cd community
./gradlew bootJar

# 5. 환경 변수 설정
cat > .env << 'EOF'
DB_URL=jdbc:mysql://localhost:3306/community
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=your_secret_key_256bit
AWS_S3_BUCKET=your_bucket
AWS_REGION=ap-northeast-2
FRONTEND_URL=http://localhost:3000
EOF

chmod 600 .env

# 6. 애플리케이션 실행
java -Xmx512m -Xms256m -jar build/libs/community-0.0.1-SNAPSHOT.jar
```

**예상 시간:** 10-15분

---

## 체크리스트

### 배포 전
- [ ] EC2 인스턴스 생성 (Amazon Linux 2, t3.small 이상)
- [ ] 보안 그룹 설정 (22, 80, 443, 8080 포트)
- [ ] RDS 또는 로컬 MySQL 준비
- [ ] S3 버킷 생성 및 IAM 권한 설정
- [ ] JWT_SECRET 생성: `openssl rand -base64 32`
- [ ] .env 파일 준비

### 배포 후
- [ ] 애플리케이션 로그 확인
- [ ] API 엔드포인트 테스트: `curl http://localhost:8080/posts`
- [ ] 데이터베이스 스키마 검증
- [ ] S3 이미지 업로드 테스트
- [ ] systemd 서비스 활성화: `sudo systemctl enable community`

---

## 배포 시나리오

### 시나리오 A: 로컬 개발 (로컬 MySQL)

**환경:** EC2 t3.micro, 1GB RAM
**설정 시간:** ~15분
**상세 가이드:** EC2-DEPENDENCIES.md

```bash
# 1. Java + 기본 도구
sudo yum install -y java-21-amazon-corretto-devel git

# 2. MySQL 로컬 설치
sudo yum install -y mysql-server
sudo systemctl start mysqld

# 3. 프로젝트 빌드 및 실행
./gradlew bootJar
java -jar build/libs/community-0.0.1-SNAPSHOT.jar
```

---

### 시나리오 B: 운영 배포 (RDS + ALB)

**환경:** EC2 t3.small, RDS, ALB
**설정 시간:** ~30분
**상세 가이드:** EC2-DEPENDENCIES.md Section 7

**아키텍처:**
```
Route 53 → ALB (80/443) → EC2 (8080) → RDS MySQL
                                      → S3 (이미지)
```

**주요 설정:**
```bash
# 1. Java + 모니터링 도구
sudo yum install -y java-21-amazon-corretto-devel git curl htop

# 2. RDS 설정 (AWS 콘솔)
#    - MySQL 8.0, 보안 그룹, 자동 백업

# 3. systemd 서비스 등록
#    - 상세: EC2-DEPENDENCIES.md

# 4. ALB 설정
#    - Target Group: EC2 (8080)
#    - Listener: 80/443
```

---

### 시나리오 C: CI/CD 자동 배포

**환경:** GitHub Actions + Jenkins + ECR + SSM
**설정 시간:** 초기 1시간, 이후 배포 7-11분
**상세 가이드:** CI-CD.md

**파이프라인:**
```
PR Merge → GitHub Actions (Test/Build/ECR Push)
        → Jenkins (Target Group 조회, SSM 로드, SSH 배포)
        → EC2 (Docker 컨테이너 재시작)
```

**핵심 기능:**
- 무중단 배포 (Target Group 기반)
- IP 하드코딩 없음 (ASG 대응)
- 환경 변수 중앙 관리 (SSM Parameter Store)
- 롤백 용이 (ECR 이미지 버전 관리)

---

## 환경별 설정

### 개발 환경
```bash
# .env
DB_URL=jdbc:mysql://localhost:3306/community
JWT_SECRET=dev_secret_key_short_ok
AWS_S3_BUCKET=ktb-3-community-images-dev
FRONTEND_URL=http://localhost:3000
```

### 스테이징 환경
```bash
# .env
DB_URL=jdbc:mysql://<rds-staging>.rds.amazonaws.com:3306/community
JWT_SECRET=staging_secret_key_long_256bit
AWS_S3_BUCKET=ktb-3-community-images-staging
FRONTEND_URL=https://staging.community.example.com
```

### 프로덕션 환경
```bash
# .env (또는 AWS Systems Manager Parameter Store)
DB_URL=jdbc:mysql://<rds-prod>.rds.amazonaws.com:3306/community
JWT_SECRET=prod_secret_key_very_long_256bit_or_more
AWS_S3_BUCKET=ktb-3-community-images-prod
FRONTEND_URL=https://community.example.com
```

---

## 시스템 요구사항

| 항목 | 최소 | 권장 | 프로덕션 |
|------|------|------|---------|
| **EC2** | t3.micro | t3.small | t3.medium+ |
| **vCPU** | 1 | 2 | 2-4 |
| **RAM** | 1GB | 2GB | 4-8GB |
| **디스크** | 10GB | 20GB | 50GB+ |
| **DB** | Local MySQL | RDS db.t3.micro | RDS db.t3.small |

### 배포 크기 추정

| 컴포넌트 | 크기 | 비고 |
|---------|------|------|
| 애플리케이션 JAR | ~20MB | Spring Boot 포함 |
| Java 21 JDK | ~450MB | Amazon Corretto |
| MySQL 8.0 | ~1GB | 로컬 설치 시만 |
| Gradle | ~100MB | 첫 실행 시 자동 다운로드 |
| **총 필요 공간** | **최소 2GB** | **권장 10GB+** |

**런타임 메모리 사용량:**
- JVM Heap: 512MB (권장 `-Xmx512m`)
- OS/Buffer: ~200MB
- MySQL: ~100MB (로컬 설치 시)
- **총 RAM**: 최소 1GB, 권장 2GB+

---

## 보안 가이드

### 1. AWS IAM
```bash
# EC2 인스턴스에 IAM Role 할당
# 권한:
# - S3: s3:GetObject, s3:PutObject
# - RDS: rds-db:connect (IAM DB 인증)
# - CloudWatch: cloudwatch:PutMetricData
```

### 2. MySQL
```bash
# 보안 그룹 설정
# - RDS 인바운드: EC2 보안 그룹만 (포트 3306)
# - 로컬 MySQL: localhost만 허용
```

### 3. S3
```bash
# 버킷 정책 (최소 권한)
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "AWS": "arn:aws:iam::ACCOUNT:role/EC2InstanceRole"
    },
    "Action": ["s3:GetObject", "s3:PutObject"],
    "Resource": "arn:aws:s3:::your-bucket/*"
  }]
}
```

### 4. SSH
```bash
# 키 페어 관리
chmod 400 your-key.pem
ssh -i your-key.pem ec2-user@your-instance

# 비밀번호 로그인 비활성화
# /etc/ssh/sshd_config: PasswordAuthentication no
```

### 5. 환경 변수
```bash
# .env 파일 권한
chmod 600 .env

# 민감한 정보는 AWS Secrets Manager 사용 권장
aws secretsmanager create-secret --name community/db-password
```

---

## 모니터링 및 로깅

### CloudWatch 로그
```bash
# CloudWatch Logs Agent 설치
wget https://s3.amazonaws.com/amazoncloudwatch-agent/amazon_linux/amd64/latest/amazon-cloudwatch-agent.rpm
sudo rpm -U ./amazon-cloudwatch-agent.rpm

# 설정
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config \
  -m ec2 \
  -s
```

### 애플리케이션 로그
```bash
# systemd 저널
sudo journalctl -u community -f  # 실시간
sudo journalctl -u community -n 100  # 최근 100줄

# 파일 기반 로그
tail -f /opt/community/logs/application.log
```

### 성능 모니터링
```bash
# JVM 메트릭
jstat -gc <pid> 1000  # 1초마다 GC 통계

# 시스템 리소스
top -p <pid>  # CPU, 메모리
iostat -x 1   # 디스크 I/O

# 네트워크
ss -tulpn | grep 8080  # 포트 확인
```

---

## 일반적인 문제 및 해결

### "Access Denied" (S3)
```bash
# 원인: IAM 권한 부족
# 해결:
# 1. EC2 인스턴스 IAM Role 확인
# 2. S3 버킷 정책 확인
# 3. CloudTrail로 실패 로그 확인

aws s3 ls s3://your-bucket/  # 테스트
```

### "MySQL connection failed"
```bash
# 원인: DB 연결, 보안 그룹, 자격증명
# 해결:
mysql -h <rds-endpoint> -u admin -p  # 직접 테스트
telnet <rds-endpoint> 3306  # 포트 확인
```

### "Out of memory"
```bash
# 원인: JVM 힙 크기 부족
# 해결:
java -Xmx1024m -Xms512m -jar app.jar  # 힙 크기 증가
```

### "Port 8080 already in use"
```bash
# 원인: 프로세스 충돌
# 해결:
lsof -i :8080  # 프로세스 확인
kill -9 <PID>  # 종료
```

---

## 추가 자료

### 프로젝트 문서
- **기술 스택**: @docs/be/LLD.md Section 1
- **데이터베이스 설계**: @docs/be/DDL.md
- **API 명세**: @docs/be/API.md
- **환경 설정**: @docs/be/LLD.md Section 10

### AWS 공식 문서
- [EC2 시작 가이드](https://docs.aws.amazon.com/ec2/index.html)
- [RDS MySQL 설정](https://docs.aws.amazon.com/rds/latest/UserGuide/CHAP_MySQL.html)
- [S3 버킷 정책](https://docs.aws.amazon.com/s3/latest/userguide/bucket-policies.html)
- [IAM 역할](https://docs.aws.amazon.com/iam/latest/userguide/id_roles.html)

### Spring Boot 문서
- [Spring Boot 배포](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Spring Security](https://spring.io/projects/spring-security)

---

## 다음 단계

1. **선택** 
   - 최소 배포: EC2-QUICK-SETUP.sh 실행
   - 상세 배포: EC2-DEPENDENCIES.md 참고

2. **검증**
   - API 테스트: `curl http://localhost:8080/posts`
   - 로그 확인: `sudo journalctl -u community -f`
   - 데이터베이스 검증

3. **운영**
   - CloudWatch 모니터링 설정
   - 로그 로테이션 설정
   - 정기 백업 스케줄

4. **최적화**
   - JVM 힙 크기 튜닝
   - 데이터베이스 인덱스 검증
   - CDN (CloudFront) 추가

---

## 지원 및 피드백

- **문제 보고**: GitHub Issues
- **개선 제안**: GitHub Discussions
- **상담**: 프로젝트 관리자 연락

---

**마지막 업데이트:** 2025-11-05  
**작성자:** Claude Code  
**상태:** 프로덕션 배포 검증 완료  
**버전:** 1.0
