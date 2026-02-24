# EC2 배포 의존성 가이드

## 개요

KTB Community Spring Boot 백엔드를 AWS EC2에 **수동으로 배포**하기 위한 완전한 의존성 목록입니다.

**CI/CD 자동 배포는** `CI-CD.md`를 참조하세요.

---

## 기술 스택 (공통 정보)

**@docs/deployment/README.md Section "기술 스택 및 버전 정보" 참조**

**주요 컴포넌트:**
- Java 21 JDK (Spring Boot 3.5.6)
- MySQL 8.0+ (RDS 또는 로컬 설치)
- Gradle 8.14.3 (Gradle Wrapper 포함)
- AWS S3 (이미지 저장소)

---

## 필수 의존성

### 1. Java 21 JDK

**용도:** Spring Boot 애플리케이션 런타임

**버전:** Java 21 이상 (프로젝트 build.gradle에서 명시)

#### Amazon Linux 2 설치
```bash
# Amazon Corretto 21 설치 (AWS 권장)
sudo yum update -y
sudo yum install java-21-amazon-corretto-devel -y

# 버전 확인
java -version
javac -version

# 결과 예시:
# openjdk 21.0.1 2023-10-17 LTS
# OpenJDK Runtime Environment Corretto-21.0.1.12.1
```

**대체 옵션:**
```bash
# OpenJDK 설치 (공개 소스)
sudo yum install java-21-openjdk-devel -y

# Oracle JDK (라이선스 필요)
# https://www.oracle.com/java/technologies/downloads/
```

#### Ubuntu 설치
```bash
sudo apt update
sudo apt install openjdk-21-jdk -y

# 버전 확인
java -version
```

#### 환경 변수 설정
```bash
# ~/.bashrc 또는 ~/.bash_profile에 추가
export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto  # Amazon Linux 2
# export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64  # Ubuntu

export PATH=$JAVA_HOME/bin:$PATH

# 변경사항 적용
source ~/.bashrc
```

**검증:**
```bash
java -version  # Java 21 확인
$JAVA_HOME/bin/javac -version  # 컴파일러 확인
```

---

### 2. MySQL 8.0+ (또는 RDS)

**용도:** 사용자, 게시글, 댓글, 좋아요 등 데이터 저장소

**버전:** 8.0 이상 (프로젝트 설정: MySQL 8.0+)

#### 옵션 A: EC2 로컬 MySQL 설치 (개발/테스트)

**Amazon Linux 2:**
```bash
# MySQL 설치
sudo yum install mysql-server -y

# MySQL 시작
sudo systemctl start mysqld
sudo systemctl enable mysqld  # 부팅 시 자동 시작

# 상태 확인
sudo systemctl status mysqld

# 초기 설정 (root 비밀번호 설정)
sudo mysql_secure_installation

# MySQL 접속 확인
mysql -u root -p
```

**Ubuntu:**
```bash
sudo apt update
sudo apt install mysql-server -y

sudo systemctl start mysql
sudo systemctl enable mysql

sudo mysql_secure_installation

mysql -u root -p
```

#### 옵션 B: AWS RDS 사용 (운영 환경 권장)

**이점:**
- 자동 백업, 복구, 패치
- Multi-AZ 자동 장애 조치
- 확장성 (읽기 복제본)
- 운영 부담 감소

**RDS 설정 후:**
```bash
# EC2에서 RDS 연결 확인
mysql -h <RDS-ENDPOINT> -u admin -p -D community

# 예시:
mysql -h community.c8h9v1j7s0p1.ap-northeast-2.rds.amazonaws.com \
      -u admin -p -D community
```

#### 데이터베이스 생성

```sql
-- MySQL 접속 후 실행
CREATE DATABASE community;
USE community;

-- 스키마 자동 생성 (JPA: ddl-auto: validate)
-- application.yaml의 Spring JPA 설정이 스키마 검증
-- DDL.md의 테이블 구조가 자동 생성됨
```

**참고:** 
- `application.yaml`의 `ddl-auto: validate` 설정으로 스키마 검증 (생성 아님)
- 초기 배포 시 `ddl-auto: create` 또는 수동 DDL 실행 필요
- 상세: @docs/be/DDL.md

---

### 3. Gradle (Gradle Wrapper 포함)

**용도:** Spring Boot 빌드, 의존성 관리

**버전:** 8.14.3 (프로젝트 gradle/wrapper/gradle-wrapper.properties에 명시)

**설치 불필요:** Gradle Wrapper가 프로젝트에 포함됨

```bash
# EC2에서 프로젝트 클론 후 빌드 (자동 Gradle 다운로드)
cd community
./gradlew build  # Linux/Mac에서 자동 실행

# 또는 Java만으로 빌드 (권장)
./gradlew bootJar  # JAR 파일 생성
```

**선택적: 시스템 전역 Gradle 설치** (권장 안 함)
```bash
# 필요 시에만
sudo yum install gradle -y  # Amazon Linux 2
# sudo apt install gradle -y  # Ubuntu

gradle --version
```

---

### 4. 시스템 라이브러리

**용도:** 개발 도구, 런타임 라이브러리

#### Amazon Linux 2
```bash
# 기본 개발 도구
sudo yum install -y \
  gcc \
  make \
  kernel-devel \
  git \
  curl \
  wget \
  tar \
  unzip \
  vim \
  htop \
  net-tools \
  openssl

# 확인
gcc --version
git --version
```

#### Ubuntu
```bash
sudo apt install -y \
  build-essential \
  git \
  curl \
  wget \
  tar \
  unzip \
  vim \
  htop \
  net-tools \
  openssl

apt list --installed | grep gcc
```

---

## 5. 빠른 배포 팁

### 자동화 스크립트 작성

위의 Step 1-4를 bash 스크립트로 작성하여 자동화할 수 있습니다.

**예시 스크립트:**
```bash
#!/bin/bash
set -e

echo "=== EC2 배포 자동화 스크립트 ==="

# 1. 시스템 업데이트 및 도구 설치
sudo yum update -y
sudo yum install -y java-21-amazon-corretto-devel git mysql-server curl

# 2. MySQL 시작
sudo systemctl start mysqld
sudo systemctl enable mysqld

# 3. 프로젝트 클론 및 빌드
GIT_REPO="https://github.com/<your-org>/community.git"
git clone $GIT_REPO
cd community

# 4. 환경 변수 설정 (.env 파일 생성)
cat > .env << 'EOF'
DB_URL=jdbc:mysql://localhost:3306/community
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=your_256bit_secret_key
AWS_S3_BUCKET=ktb-3-community-images
AWS_REGION=ap-northeast-2
FRONTEND_URL=http://localhost:3000
EOF

chmod 600 .env

# 5. 빌드 및 실행
./gradlew bootJar
java -Xmx512m -Xms256m -jar build/libs/community-0.0.1-SNAPSHOT.jar
```

**사용 방법:**
```bash
# 1. 스크립트 작성
vim deploy.sh
# (위 내용 붙여넣기)

# 2. 실행 권한 부여
chmod +x deploy.sh

# 3. 실행
./deploy.sh
```

**참고:**
- 실제 배포 시 `GIT_REPO`, `DB_PASSWORD`, `JWT_SECRET` 등을 실제 값으로 변경하세요
- systemd 서비스로 등록하려면 아래 "선택적 의존성" 섹션 참조

---

## 환경 변수 설정 (상세 가이드)

**7개 필수 변수 개요**: @docs/deployment/README.md Section "필수 환경 변수" 참조

**이 섹션의 내용**: 상세 설정 방법 3가지 (`.env`, `systemd`, `AWS SSM`)

### 방법 1: .env 파일 (spring-dotenv)

```bash
# .env 파일 생성
cat > .env << 'EOF'
DB_URL=jdbc:mysql://localhost:3306/community
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=$(openssl rand -base64 32)
AWS_S3_BUCKET=ktb-3-community-images
AWS_REGION=ap-northeast-2
FRONTEND_URL=http://localhost:3000
EOF

# 권한 설정 (보안)
chmod 600 .env
```

**장점**: 간단하고 로컬 개발에 적합
**주의**: `.env` 파일을 Git에 커밋하지 마세요 (`.gitignore`에 추가)

### 방법 2: systemd EnvironmentFile

**서비스 파일**: `/etc/systemd/system/community.service`

```ini
[Unit]
Description=KTB Community Backend
After=network.target mysql.service

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/opt/community
EnvironmentFile=/opt/community/.env
ExecStart=/usr/bin/java -jar /opt/community/community.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

**장점**: 프로덕션 환경, 자동 재시작
**설정**: `sudo systemctl daemon-reload && sudo systemctl enable community`

### 방법 3: AWS SSM Parameter Store (CI/CD)

**파라미터 생성**:
```bash
aws ssm put-parameter \
  --name "/community/DB_URL" \
  --value "jdbc:mysql://your-rds-endpoint:3306/community" \
  --type "String"

aws ssm put-parameter \
  --name "/community/DB_PASSWORD" \
  --value "your_password" \
  --type "SecureString"  # 암호화됨

# 나머지 파라미터도 동일하게 생성
```

**장점**: 중앙 관리, 버전 관리, 암호화, IAM 권한 제어
**사용처**: CI/CD 파이프라인 (Jenkins, GitHub Actions)

---

## 선택적 의존성

### 1. 모니터링 및 진단

```bash
# Amazon Linux 2
sudo yum install -y \
  htop        # 프로세스 모니터링
  iotop       # 디스크 I/O 모니터링
  nethogs     # 네트워크 모니터링
  lsof        # 열린 파일 확인

# 사용 예시
htop        # 실시간 프로세스
iotop       # 디스크 I/O
ss -tulpn   # 네트워크 포트 확인
```

### 2. 로그 로테이션

```bash
# logrotate 설치 (대부분 기본 설치)
# /etc/logrotate.d/community 설정 예시
cat > /tmp/community-logrotate << 'EOF'
/opt/community/logs/application.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    create 0640 ubuntu ubuntu
    sharedscripts
}
EOF

sudo cp /tmp/community-logrotate /etc/logrotate.d/community
sudo logrotate -f /etc/logrotate.d/community  # 테스트
```

### 3. 프로세스 관리

#### systemd 서비스 (권장)

```bash
# /etc/systemd/system/community.service
sudo tee /etc/systemd/system/community.service > /dev/null <<EOF
[Unit]
Description=KTB Community Spring Boot Application
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/community
ExecStart=/usr/bin/java \
  -Xmx512m \
  -Xms256m \
  -Dspring.config.location=file:/opt/community/.env \
  -jar /opt/community/build/libs/community-0.0.1-SNAPSHOT.jar
SuccessExitStatus=0
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 서비스 활성화
sudo systemctl daemon-reload
sudo systemctl enable community
sudo systemctl start community
sudo systemctl status community

# 로그 확인
sudo journalctl -u community -f  # 실시간
sudo journalctl -u community -n 50  # 최근 50줄
```

#### supervisor 사용 (대체 옵션)

```bash
sudo yum install supervisor -y

# /etc/supervisord.d/community.ini
sudo tee /etc/supervisord.d/community.ini > /dev/null <<EOF
[program:community]
command=/usr/bin/java -Xmx512m -Xms256m -jar /opt/community/build/libs/community-0.0.1-SNAPSHOT.jar
directory=/opt/community
user=ubuntu
autostart=true
autorestart=true
redirect_stderr=true
stdout_logfile=/opt/community/logs/supervisor.log
EOF

sudo systemctl start supervisord
sudo systemctl enable supervisord
```

### 4. Nginx 리버스 프록시 (선택적)

```bash
# Nginx 설치
sudo yum install nginx -y  # Amazon Linux 2
# sudo apt install nginx -y  # Ubuntu

# /etc/nginx/conf.d/community.conf
sudo tee /etc/nginx/conf.d/community.conf > /dev/null <<EOF
upstream community_backend {
    server localhost:8080;
}

server {
    listen 80;
    server_name _;

    client_max_body_size 10M;  # application.yaml max-request-size 과 일치

    location / {
        proxy_pass http://community_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        
        # WebSocket 지원 (향후 필요 시)
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
EOF

# Nginx 테스트 및 시작
sudo nginx -t
sudo systemctl start nginx
sudo systemctl enable nginx
```

### 5. Docker (선택적, 컨테이너 배포 시)

```bash
# Docker 설치
sudo yum update -y
sudo yum install docker -y  # Amazon Linux 2
# sudo apt install docker.io -y  # Ubuntu

# Docker 시작
sudo systemctl start docker
sudo systemctl enable docker

# 권한 설정 (ubuntu 사용자)
sudo usermod -aG docker ubuntu
newgrp docker

# 검증
docker --version
docker ps
```

---

## 환경 변수 설정

### 필수 환경 변수

프로젝트는 `.env` 파일 또는 시스템 환경 변수에서 다음을 읽습니다.

#### 1. 데이터베이스
```bash
# EC2 로컬 MySQL
DB_URL=jdbc:mysql://localhost:3306/community
DB_USERNAME=root
DB_PASSWORD=<MySQL-Root-Password>

# RDS 사용 시
DB_URL=jdbc:mysql://<RDS-ENDPOINT>:3306/community
DB_USERNAME=admin
DB_PASSWORD=<RDS-Master-Password>
```

#### 2. JWT 보안
```bash
# 256비트 이상 시크릿 키 생성
JWT_SECRET=your-256bit-or-longer-secret-key-here

# 생성 방법:
# openssl rand -base64 32  (Linux)
# date | sha256sum | head -c 32  (간단한 방법)
```

#### 3. AWS S3 (이미지 저장)
```bash
# 옵션 1: IAM 사용자 인증 정보
AWS_ACCESS_KEY_ID=<IAM-User-Access-Key>
AWS_SECRET_ACCESS_KEY=<IAM-User-Secret-Key>
AWS_S3_BUCKET=ktb-3-community-images-prod
AWS_REGION=ap-northeast-2

# 옵션 2: EC2 IAM Role (권장)
# - S3 접근 권한이 있는 IAM Role을 EC2에 할당
# - 환경 변수 불필요 (DefaultCredentialsProvider 체인 사용)

# 옵션 3: ~/.aws/credentials 파일
# ~/.aws/credentials
[default]
aws_access_key_id=AKIAIOSFODNN7EXAMPLE
aws_secret_access_key=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

#### 4. Frontend URL (CORS)
```bash
# Express.js 프론트엔드 주소 (기본값: localhost:3000)
FRONTEND_URL=https://community.example.com

# 개발 환경
FRONTEND_URL=http://localhost:3000
```

### 환경 변수 설정 방법

#### 방법 1: .env 파일 (spring-dotenv)
```bash
# /opt/community/.env (프로젝트 루트)
DB_URL=jdbc:mysql://localhost:3306/community
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=your_secret_key
AWS_S3_BUCKET=your_bucket
AWS_REGION=ap-northeast-2
FRONTEND_URL=http://localhost:3000

# 파일 권한 설정 (민감 정보 보호)
chmod 600 .env
```

#### 방법 2: 시스템 환경 변수
```bash
# /etc/environment (시스템 전역)
export DB_URL=jdbc:mysql://localhost:3306/community
export DB_USERNAME=root
export DB_PASSWORD=your_password
export JWT_SECRET=your_secret_key
export AWS_S3_BUCKET=your_bucket
export AWS_REGION=ap-northeast-2
export FRONTEND_URL=http://localhost:3000

# 또는 systemd 서비스에서
[Service]
Environment="DB_URL=jdbc:mysql://localhost:3306/community"
Environment="DB_PASSWORD=your_password"
...
```

#### 방법 3: systemd 서비스 (권장)
```bash
# /etc/systemd/system/community.service에 Environment 추가
[Service]
Environment="DB_URL=jdbc:mysql://localhost:3306/community"
Environment="DB_USERNAME=root"
Environment="DB_PASSWORD=your_password"
Environment="JWT_SECRET=your_secret_key"
Environment="AWS_S3_BUCKET=your_bucket"
Environment="AWS_REGION=ap-northeast-2"
Environment="FRONTEND_URL=http://localhost:3000"
```

---

## 설치 순서 (권장)

EC2 인스턴스에 처음 배포할 때 다음 순서로 설치하세요:

### Step 1: 시스템 업데이트 및 기본 도구 (5분)
```bash
sudo yum update -y  # Amazon Linux 2
# sudo apt update && sudo apt upgrade -y  # Ubuntu

# 기본 도구 설치
sudo yum install -y git curl wget tar unzip gcc make openssl
```

### Step 2: Java 21 설치 (2분)
```bash
sudo yum install java-21-amazon-corretto-devel -y
java -version  # 확인

# JAVA_HOME 설정
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto' >> ~/.bashrc
source ~/.bashrc
```

### Step 3: MySQL 설정 (5분, RDS 선택 시 생략)
```bash
# RDS 사용 시: AWS 콘솔에서 RDS 인스턴스 생성 후 다음 명령어로 연결 테스트
mysql -h <RDS-ENDPOINT> -u admin -p

# 로컬 MySQL 사용 시:
sudo yum install mysql-server -y
sudo systemctl start mysqld
sudo systemctl enable mysqld
mysql -u root -p  # 연결 테스트
```

### Step 4: 프로젝트 클론 및 빌드 (5-10분)
```bash
cd /opt
sudo git clone https://github.com/<your-repo>/community.git
cd community

# Gradle Wrapper로 빌드
./gradlew build -x test  # 테스트 제외
# 또는
./gradlew bootJar

# JAR 파일 생성 확인
ls -lh build/libs/community-0.0.1-SNAPSHOT.jar
```

### Step 5: 환경 변수 설정 (5분)
```bash
# .env 파일 생성
cat > /opt/community/.env << 'EOF'
DB_URL=jdbc:mysql://localhost:3306/community
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=your_secret_key
AWS_S3_BUCKET=your_bucket
AWS_REGION=ap-northeast-2
FRONTEND_URL=http://localhost:3000
EOF

chmod 600 .env
```

### Step 6: 데이터베이스 초기화 (2분, 첫 실행만)
```bash
# MySQL 접속
mysql -u root -p

# 데이터베이스 생성
CREATE DATABASE community;
USE community;

# application.yaml의 ddl-auto: validate이므로
# 첫 실행 시 Hibernate가 스키마 검증/생성
# 상세: @docs/be/DDL.md
```

### Step 7: 애플리케이션 실행 (2분)
```bash
# 첫 실행 (전경)
cd /opt/community
java -Xmx512m -Xms256m -jar build/libs/community-0.0.1-SNAPSHOT.jar

# 또는 nohup으로 백그라운드 실행
nohup java -Xmx512m -Xms256m -jar build/libs/community-0.0.1-SNAPSHOT.jar > logs/app.log 2>&1 &

# systemd 서비스로 관리하는 경우 Step 8 실행
```

### Step 8: systemd 서비스 설정 (권장, 자동 시작)
```bash
# 위의 "선택적 의존성 - 3. 프로세스 관리" 섹션 참조

sudo systemctl start community
sudo systemctl enable community
sudo systemctl status community
```

### Step 9: 검증 (2분)
```bash
# 포트 확인
ss -tulpn | grep 8080

# 로그 확인
tail -f /opt/community/logs/application.log

# API 테스트
curl http://localhost:8080/posts

# 정상 응답 예시: 200 OK + JSON
```

---

## 시스템 요구사항 정리

| 항목 | 사양 | 이유 |
|------|------|------|
| **OS** | Amazon Linux 2 또는 Ubuntu 20.04+ | AWS 권장, 패키지 풍부 |
| **vCPU** | 최소 1개, 권장 2개+ | 빌드(Gradle), 실행 동시 처리 |
| **메모리** | 최소 2GB, 권장 4GB+ | JVM heap(-Xmx512m), MySQL, OS |
| **디스크** | 최소 20GB, 권장 50GB+ | 로그, 데이터베이스, 이미지 캐시 |
| **네트워크** | 1Mbps 이상 | API 응답, 로그 수집 |
| **보안 그룹** | 22(SSH), 80/443(HTTP/HTTPS), 8080(API) | 원격 접속, 웹 서비스 |

---

## 트러블슈팅

### Java 버전 오류
```bash
# 오류: "Java 21 not found" 또는 "Unsupported class version 65.0"
java -version  # 버전 확인

# 해결: 정확한 Java 21 설치
sudo yum install java-21-amazon-corretto-devel -y

# 여러 버전 설치 시 기본 버전 변경
sudo alternatives --config java
```

### MySQL 연결 오류
```bash
# 오류: "Communications link failure" 또는 "No route to host"
mysql -h <host> -u root -p  # 직접 연결 테스트

# 해결: 
# 1. RDS 보안 그룹 확인 (EC2 보안 그룹 허용)
# 2. MySQL 서버 실행 확인: sudo systemctl status mysqld
# 3. 방화벽 확인: sudo systemctl status firewalld
```

### Gradle 빌드 실패
```bash
# 오류: "Gradle Wrapper not executable" 또는 "build failed"
chmod +x gradlew  # 실행 권한 설정
./gradlew build --stacktrace  # 상세 오류 출력

# 캐시 클리어
./gradlew clean build
```

### 포트 이미 사용 중
```bash
# 오류: "Address already in use :8080"
lsof -i :8080  # 포트 사용 프로세스 확인
kill -9 <PID>  # 프로세스 종료

# 또는 다른 포트 사용
java -Dserver.port=8081 -jar ...
```

### S3 권한 오류
```bash
# 오류: "Access Denied" or "The Access Key Id you provided does not exist"
# 원인: IAM 사용자 권한 또는 credentials 오류

# 해결 1: IAM Role 사용 (권장)
# EC2 인스턴스에 S3 접근 권한의 IAM Role 할당

# 해결 2: IAM 사용자 키 확인
cat ~/.aws/credentials  # 키 확인
# 또는 환경 변수 확인
echo $AWS_ACCESS_KEY_ID

# 해결 3: IAM Policy 확인
# S3 버킷 접근 권한이 있는지 AWS 콘솔에서 확인
```

---

## 보안 체크리스트

- [ ] `JWT_SECRET` 256비트 이상, 강력한 난수 생성
- [ ] `.env` 파일 권한 설정: `chmod 600`
- [ ] MySQL root 비밀번호 설정: `mysql_secure_installation`
- [ ] 불필요한 포트 닫음 (방화벽)
- [ ] SSH 키 기반 인증 설정
- [ ] SSL/TLS 인증서 설치 (Nginx + Let's Encrypt)
- [ ] 로그 정기 로테이션
- [ ] EC2 보안 그룹: 필요한 포트만 열기
- [ ] RDS 보안 그룹: EC2 보안 그룹만 허용
- [ ] S3 버킷 정책: 최소 권한 원칙

---

## 모니터링 및 유지보수

### 헬스 체크
```bash
# 정기적으로 API 호출 (모니터링 도구 또는 cron)
curl -f http://localhost:8080/posts || \
  systemctl restart community && \
  echo "Community service restarted at $(date)" >> /var/log/community-health.log
```

### 로그 모니터링
```bash
# 실시간 로그 확인
tail -f /opt/community/logs/application.log

# 에러만 필터링
grep "ERROR\|WARN" /opt/community/logs/application.log

# 일시별 로그 분석
grep "2025-11-05" /opt/community/logs/application.log | wc -l
```

### 성능 모니터링
```bash
# JVM 메모리 사용량
jps -l -m  # 실행 중인 Java 프로세스
jstat -gc <pid> 1000  # GC 통계 (1초마다)

# 시스템 리소스
top -p <pid>  # CPU, 메모리 사용량
iostat -x 1  # 디스크 I/O
```

---

## 참고 문서

- **프로젝트 기술 스택**: @docs/be/LLD.md Section 1
- **데이터베이스 스키마**: @docs/be/DDL.md
- **환경 변수 설정**: @docs/be/LLD.md Section 10
- **배포 시 성능 튜닝**: @docs/be/LLD.md Section 12
- **보안 설정**: @docs/be/LLD.md Section 6

---

## 버전 정보

| 컴포넌트 | 버전 | 파일 |
|---------|------|------|
| Java | 21 | build.gradle:13 |
| Spring Boot | 3.5.6 | build.gradle:3 |
| Gradle | 8.14.3 | gradle/wrapper/gradle-wrapper.properties |
| MySQL | 8.0+ | application.yaml:9 |
| MySQL Connector | (자동) | build.gradle:36 |
| AWS SDK | 2.20.0 | build.gradle:61 |
| Bucket4j (Rate Limit) | 8.10.1 | build.gradle:49 |

---

**마지막 업데이트:** 2025-11-05  
**작성자:** Claude Code  
**상태:** 프로덕션 배포 준비 완료
