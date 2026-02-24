# CI/CD Pipeline Documentation

DC2 Community 프로젝트의 지속적 통합/배포(CI/CD) 파이프라인 아키텍처 및 운영 가이드

**수동 배포는** `EC2-DEPENDENCIES.md`를 참조하세요.

---

## 📋 목차

1. [파이프라인 개요](#파이프라인-개요)
2. [GitHub Actions (Build & Test)](#github-actions-build--test)
3. [Jenkins (Deployment)](#jenkins-deployment)
4. [AWS ECR (Container Registry)](#aws-ecr-container-registry)
5. [배포 플로우](#배포-플로우)
6. [환경 변수 관리](#환경-변수-관리)
7. [트러블슈팅](#트러블슈팅)

---

## 파이프라인 개요

### 아키텍처

```
┌──────────────────────────────────────────────────────────────────┐
│  Developer                                                       │
│    ↓ git push origin main → merge to deploy branch               │
└────┬─────────────────────────────────────────────────────────────┘
     │
     ▼
┌──────────────────────────────────────────────────────────────────┐
│  GitHub Actions (.github/workflows/cd.yml)                       │
│  ├─ Stage 1: Test (gradlew build test)                           │
│  ├─ Stage 2: Build & Push Image                                  │
│  │    ├─ AWS OIDC 인증                                            │
│  │    ├─ ECR 로그인                                                │
│  │    ├─ Docker 빌드 (linux/amd64)                                │
│  │    └─ ECR 푸시 (be-{timestamp}, be-latest)                     │
│  └─ Stage 3: Trigger Jenkins                                     │
│       └─ POST /job/community-backend-deploy/buildWithParameters  │
└────┬─────────────────────────────────────────────────────────────┘
     │
     ▼
┌──────────────────────────────────────────────────────────────────┐
│  Jenkins (Jenkinsfile.backend)                                   │
│  ├─ Stage 1: Checkout                                            │
│  ├─ Stage 2: Resolve Backend Targets (from ALB Target Group)     │
│  ├─ Stage 3: Load Backend Env From SSM                           │
│  │    └─ /community/* parameters (DB, JWT, S3, FE URL)           │
│  └─ Stage 4: Deploy Backend                                      │
│       ├─ SSH to each EC2 instance                                │
│       └─ Execute /mnt/efs/deploy/backend/deploy-backend.sh       │
└────┬─────────────────────────────────────────────────────────────┘
     │
     ▼
┌──────────────────────────────────────────────────────────────────┐
│  EC2 Instances (Backend)                                         │
│  ├─ ECR 로그인 (aws ecr get-login-password)                        │
│  ├─ Docker Compose Pull (새 이미지)                                │
│  ├─ Docker Compose Up -d (재배포)                                  │
│  └─ Health Check (curl /health)                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 핵심 특징

| 항목 | 설명 |
|------|------|
| **트리거** | `deploy` 브랜치 PR merge 시 자동 실행 |
| **빌드 플랫폼** | GitHub Actions (Ubuntu Latest) |
| **배포 도구** | Jenkins (SSH 배포) |
| **컨테이너 레지스트리** | AWS ECR (OIDC 인증) |
| **배포 대상** | ALB Target Group의 모든 EC2 인스턴스 |
| **환경 변수** | AWS SSM Parameter Store (중앙 관리) |
| **배포 스크립트** | EFS 공유 스크립트 (`/mnt/efs/deploy/`) |

---

## GitHub Actions (Build & Test)

### 워크플로우 파일

**경로**: `.github/workflows/cd.yml`

### 주요 Job

#### 1. Test Job

```yaml
test:
  runs-on: ubuntu-latest
  if: github.event.pull_request.merged == true
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
    - uses: actions/cache@v4  # Gradle 캐시
    - run: ./gradlew build test
```

**목적**: PR merge 시 전체 테스트 실행으로 배포 전 품질 검증

#### 2. Build Job

```yaml
build:
  runs-on: ubuntu-latest
  needs: test
  permissions:
    id-token: write  # OIDC 토큰 발급 필수
    contents: read
  outputs:
    image_tag: ${{ steps.tag.outputs.TAG }}
  steps:
    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v4
      with:
        role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
        aws-region: ap-northeast-2

    - name: Login to Amazon ECR
      uses: aws-actions/amazon-ecr-login@v2

    - name: Generate tag
      id: tag
      run: echo "TAG=$(date +'%Y%m%d-%H%M%S')" >> $GITHUB_OUTPUT

    - name: Build and push image to ECR
      uses: docker/build-push-action@v5
      with:
        context: .
        push: true
        platforms: linux/amd64
        tags: |
          557690602093.dkr.ecr.ap-northeast-2.amazonaws.com/ktb-personal:be-latest
          557690602093.dkr.ecr.ap-northeast-2.amazonaws.com/ktb-personal:be-${{ steps.tag.outputs.TAG }}
```

**핵심 포인트**:
- **OIDC 인증**: GitHub Actions가 임시 AWS 자격증명 획득 (Access Key 불필요)
- **ECR 로그인**: `aws-actions/amazon-ecr-login@v2` 사용
- **태그 전략**: `be-latest` (최신), `be-YYYYMMDD-HHMMSS` (타임스탬프)
- **플랫폼**: `linux/amd64` 명시 (EC2 x86_64 아키텍처)

#### 3. Deploy Job (Jenkins Trigger)

```yaml
deploy:
  runs-on: ubuntu-latest
  needs: build
  steps:
    - name: Trigger Jenkins backend deploy
      env:
        IMAGE_DATE_TAG: ${{ needs.build.outputs.image_tag }}
        JENKINS_URL: ${{ secrets.JENKINS_URL }}
        JENKINS_USER: ${{ secrets.JENKINS_USER }}
        JENKINS_API_TOKEN: ${{ secrets.JENKINS_API_TOKEN }}
      run: |
        # 1) Crumb 발급 (CSRF 보호)
        CRUMB_JSON=$(curl -s -u "${JENKINS_USER}:${JENKINS_API_TOKEN}" "${JENKINS_URL}/crumbIssuer/api/json")
        CRUMB_FIELD=$(echo "$CRUMB_JSON" | jq -r '.crumbRequestField')
        CRUMB=$(echo "$CRUMB_JSON" | jq -r '.crumb')

        # 2) Jenkins 잡 파라미터 빌드 트리거
        curl -X POST "${JENKINS_URL}/job/community-backend-deploy/buildWithParameters" \
             -u "${JENKINS_USER}:${JENKINS_API_TOKEN}" \
             -H "${CRUMB_FIELD}: ${CRUMB}" \
             --data-urlencode "IMAGE_DATE_TAG=${IMAGE_DATE_TAG}"
```

**작동 방식**:
1. Jenkins CSRF Crumb 발급
2. `community-backend-deploy` Job 트리거 (파라미터: `IMAGE_DATE_TAG`)
3. Jenkins가 실제 EC2 배포 수행

### 필수 GitHub Secrets

| Secret | 설명 | 예시 |
|--------|------|------|
| `AWS_ROLE_ARN` | GitHub Actions OIDC Role | `arn:aws:iam::557690602093:role/GitHubActionsRole` |
| `REGISTRY_URL_ECR` | ECR Registry URL | `557690602093.dkr.ecr.ap-northeast-2.amazonaws.com` |
| `JENKINS_URL` | Jenkins 서버 URL | `https://jenkins.example.com` |
| `JENKINS_USER` | Jenkins 사용자 ID | `admin` |
| `JENKINS_API_TOKEN` | Jenkins API 토큰 | `11e...` |

---

## 사전 준비사항

### 필수 인프라

**@docs/deployment/README.md Section "기술 스택" 참조**

### 추가 요구사항

| 컴포넌트 | 용도 | 설정 위치 |
|---------|------|---------|
| GitHub Secrets | AWS OIDC Role, Jenkins API | Repository Settings |
| AWS OIDC Provider | GitHub Actions 인증 | IAM |
| Jenkins 서버 | Private Subnet 배포 | EC2 + IAM Role |
| ECR Repository | Docker 이미지 저장소 | ECR Console |
| SSM Parameter Store | 환경 변수 관리 | Systems Manager |
| ALB Target Group | 배포 대상 조회 | Load Balancers |

---

## Jenkins (CD)

### Jenkins 서버 설치 및 설정

#### Step 1: Jenkins 설치 (Amazon Linux 2)

```bash
sudo wget -O /etc/yum.repos.d/jenkins.repo \
  https://pkg.jenkins.io/redhat-stable/jenkins.repo
sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key
sudo yum install jenkins java-11-openjdk-devel -y

sudo systemctl start jenkins
sudo systemctl enable jenkins
```

#### Step 2: AWS CLI 설치

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
aws --version
```

#### Step 3: jq 설치 (JSON 파싱)

```bash
sudo yum install jq -y
```

#### Step 4: SSH 키 설정

```bash
sudo -u jenkins mkdir -p /var/lib/jenkins/.ssh
sudo -u jenkins ssh-keygen -t rsa -b 2048 -f /var/lib/jenkins/.ssh/id_rsa -N ""
cat /var/lib/jenkins/.ssh/id_rsa.pub
# → EC2 인스턴스의 ~/.ssh/authorized_keys에 추가
```

#### Step 5: Jenkins IAM Role 설정

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "elasticloadbalancing:DescribeTargetGroups",
      "elasticloadbalancing:DescribeTargetHealth",
      "ec2:DescribeInstances",
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath"
    ],
    "Resource": "*"
  }]
}
```

---

## Jenkins (Deployment)

### Jenkinsfile

**경로**: `Jenkinsfile.backend`

### 주요 스니펫 (Jenkinsfile.backend)

**전체 파일 경로**: `Jenkinsfile.backend` (프로젝트 루트)

### 주요 Stage

#### Stage 1: Resolve Backend Targets (ALB Target Group 조회)

```groovy
stage('Resolve Backend Targets') {
    steps {
        script {
            echo "=== Resolving backend instance IPs from Target Group ==="

            // Target Group에서 인스턴스 ID 조회
            def instanceIds = sh(
                script: """
                    aws elbv2 describe-target-health \\
                        --region ${AWS_REGION} \\
                        --target-group-arn ${BE_TG_ARN} \\
                        --query 'TargetHealthDescriptions[].Target.Id' \\
                        --output text
                """,
                returnStdout: true
            ).trim()

            // EC2 Private IP 조회
            def ipsText = sh(
                script: """
                    aws ec2 describe-instances \\
                        --region ${AWS_REGION} \\
                        --instance-ids ${instanceIds} \\
                        --query 'Reservations[].Instances[].PrivateIpAddress' \\
                        --output text
                """,
                returnStdout: true
            ).trim()

            env.BACKEND_IPS = ipsText
            echo "Backend IPs: ${env.BACKEND_IPS}"
        }
    }
}
```

**핵심**: IP 하드코딩 없이 ALB Target Group에서 동적 조회 → ASG 대응 가능

---

#### Stage 2: Load Backend Env From SSM

```groovy
stage('Load Backend Env From SSM') {
    steps {
        script {
            echo "=== Loading backend environment variables from SSM ==="

            def output = sh(
                script: """
                    aws ssm get-parameters \\
                        --names \\
                            "/community/DB_URL" \\
                            "/community/DB_USERNAME" \\
                            "/community/DB_PASSWORD" \\
                            "/community/JWT_SECRET" \\
                            "/community/AWS_S3_BUCKET" \\
                            "/community/AWS_REGION" \\
                            "/community/FRONTEND_URL" \\
                        --with-decryption \\
                        --query "Parameters[].[Name,Value]" \\
                        --output text
                """,
                returnStdout: true
            ).trim()

            // .env 파일 생성
            def envContent = ""
            output.split('\n').each { line ->
                def parts = line.split('\t')
                def key = parts[0].split('/')[-1]  // 경로에서 키만 추출
                def value = parts[1]
                envContent += "${key}=${value}\n"
            }

            env.BACKEND_ENV_CONTENT = envContent
        }
    }
}
```

**핵심**: SSM Parameter Store에서 중앙 관리된 환경 변수를 안전하게 로드

---

#### Stage 3: Deploy Backend

```groovy
stage('Deploy Backend') {
    steps {
        script {
            def backendIps = env.BACKEND_IPS.split()

            backendIps.each { ip ->
                echo "=== Deploying to Backend Instance: ${ip} ==="

                sshagent(['deploy-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ec2-user@${ip} \\
                            'bash -s' < /mnt/efs/deploy/backend/deploy-backend.sh \\
                            "${env.BACKEND_ENV_CONTENT}" \\
                            "${params.IMAGE_DATE_TAG}"
                    """
                }

                echo "Deployment to ${ip} completed"
            }
        }
    }
}
```

**핵심**:
- 조회된 모든 IP에 대해 반복 배포
- EFS 공유 스크립트(`deploy-backend.sh`) 실행
- 환경 변수와 이미지 태그를 파라미터로 전달

---

#### deploy-backend.sh 스크립트 개요

**위치**: `/mnt/efs/deploy/backend/deploy-backend.sh`

**주요 작업:**
```bash
#!/bin/bash
ENV_CONTENT=$1
IMAGE_TAG=$2

# 1. .env 파일 생성
echo "$ENV_CONTENT" > /opt/community/.env

# 2. ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | \
    docker login --username AWS --password-stdin <ECR_URI>

# 3. Docker Compose Pull & Up
cd /opt/community
docker-compose pull
docker-compose up -d

# 4. Health Check
sleep 10
curl -f http://localhost:8080/health || exit 1
```

---

### Jenkins 환경 변수

| 환경 변수 | 설명 | 소스 |
|----------|------|------|
| `AWS_REGION` | AWS 리전 | Jenkinsfile 고정값 |
| `REGISTRY` | ECR Registry URL | Jenkinsfile 고정값 |
| `IMAGE_NAME` | ECR Repository 이름 | Jenkinsfile 고정값 |
| `BE_TG_ARN` | Backend Target Group ARN | Jenkinsfile 고정값 |
| `IMAGE_DATE_TAG` | 이미지 태그 (타임스탬프) | GitHub Actions 파라미터 |
| `BE_DB_URL` | Database URL | SSM Parameter Store |
| `BE_JWT_SECRET` | JWT Secret Key | SSM Parameter Store |
| ... | (기타 환경 변수) | SSM Parameter Store |

### Jenkins SSH 인증

**방식**: SSH Key-based Authentication

**설정**:
1. Jenkins Credentials에 EC2 SSH Key 등록 (`ktb-personal-project-ec2-ssh-key`)
2. Jenkinsfile에서 `sshagent` 사용:
   ```groovy
   sshagent(credentials: ['ktb-personal-project-ec2-ssh-key']) {
       sh "ssh ubuntu@${ip} '...'"
   }
   ```

---

## AWS ECR (Container Registry)

### 리포지토리 구조

**리포지토리 이름**: `ktb-personal`

**태그 전략**:

| 태그 패턴 | 설명 | 예시 |
|----------|------|------|
| `be-latest` | Backend 최신 이미지 | `ktb-personal:be-latest` |
| `be-YYYYMMDD-HHMMSS` | Backend 특정 빌드 | `ktb-personal:be-20251208-143022` |
| `fe-latest` | Frontend 최신 이미지 | `ktb-personal:fe-latest` |
| `fe-YYYYMMDD-HHMMSS` | Frontend 특정 빌드 | `ktb-personal:fe-20251208-143022` |

**단일 리포지토리 사용 이유**:
- 프로젝트 통합 관리 (Frontend + Backend)
- ECR Free Tier 효율적 사용 (리포지토리당 500MB)
- 태그 prefix로 명확한 구분 (`be-`, `fe-`)

### OIDC 인증 (GitHub Actions)

**IAM Role Trust Relationship**:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::557690602093:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:<ORG>/<REPO>:ref:refs/heads/deploy"
        }
      }
    }
  ]
}
```

**필요 IAM 정책**:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload"
      ],
      "Resource": "*"
    }
  ]
}
```

### ECR 로그인 (EC2)

**방식 1: IAM Instance Profile (권장)**
```bash
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 557690602093.dkr.ecr.ap-northeast-2.amazonaws.com
```

**방식 2: AWS CLI Credentials**
```bash
export AWS_ACCESS_KEY_ID=xxx
export AWS_SECRET_ACCESS_KEY=yyy
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 557690602093.dkr.ecr.ap-northeast-2.amazonaws.com
```

**토큰 유효기간**: 12시간 (자동 갱신 필요)

---

## 배포 플로우

### 전체 흐름 (Step-by-Step)

```
1. 개발자: 코드 작성 및 main 브랜치에 push
2. 개발자: GitHub PR 생성 (main → deploy)
3. 개발자: PR Merge
4. GitHub Actions: cd.yml 자동 트리거
5. GitHub Actions: Test Job 실행 (./gradlew build test)
   ├─ 성공 → 다음 단계
   └─ 실패 → 배포 중단, Slack 알림
6. GitHub Actions: Build Job 실행
   ├─ AWS OIDC 인증
   ├─ ECR 로그인
   ├─ Docker 빌드 (linux/amd64)
   ├─ ECR 푸시 (be-latest, be-20251208-143022)
   └─ 이미지 태그 출력 → deploy Job으로 전달
7. GitHub Actions: Deploy Job 실행
   ├─ Jenkins Crumb 발급
   └─ Jenkins API 호출 (buildWithParameters)
8. Jenkins: community-backend-deploy Job 트리거
9. Jenkins: Checkout Stage
10. Jenkins: Resolve Backend Targets
    ├─ ALB Target Group ARN으로 인스턴스 ID 조회
    └─ 인스턴스 ID로 Private IP 조회
11. Jenkins: Load Backend Env From SSM
    ├─ SSM Parameter Store에서 환경 변수 로드
    └─ 환경 변수 검증 (누락 시 에러)
12. Jenkins: Deploy Backend Stage
    ├─ 각 EC2 인스턴스에 SSH 접속
    ├─ 환경 변수 전달
    └─ /mnt/efs/deploy/backend/deploy-backend.sh 실행
13. EC2 (deploy-backend.sh):
    ├─ ECR 로그인
    ├─ Docker Compose Pull (새 이미지)
    ├─ Docker Compose Up -d (무중단 재시작)
    └─ Health Check (curl /health)
14. 배포 완료 ✅
```

### 소요 시간

| Stage | 예상 시간 |
|-------|----------|
| Test | 2-3분 |
| Build & ECR Push | 3-5분 |
| Jenkins Trigger | 10초 |
| Resolve Targets & Load SSM | 20초 |
| Deploy to EC2 (2대) | 1-2분 |
| **총 소요 시간** | **7-11분** |

### 롤백 절차

**방법 1: 이전 이미지 태그로 재배포**
```bash
# Jenkins 수동 실행
# Parameter: IMAGE_DATE_TAG = 20251207-120000 (이전 성공 빌드)
```

**방법 2: EC2에서 직접 롤백**
```bash
ssh ubuntu@<EC2_IP>
cd /mnt/efs/deploy/backend

# docker-compose.yml 수정
export IMAGE_TAG=be-20251207-120000

docker compose pull
docker compose up -d
```

---

## 환경 변수 관리

### SSM Parameter Store 구조

| Parameter Name | Type | 설명 |
|----------------|------|------|
| `/community/DB_URL` | SecureString | MySQL 접속 URL |
| `/community/DB_USERNAME` | SecureString | MySQL 사용자명 |
| `/community/DB_PASSWORD` | SecureString | MySQL 비밀번호 |
| `/community/JWT_SECRET` | SecureString | JWT Secret Key (256bit) |
| `/community/AWS_S3_BUCKET` | String | S3 버킷 이름 |
| `/community/AWS_REGION` | String | AWS 리전 |
| `/community/FRONTEND_URL` | String | CORS용 Frontend URL |

### SSM Parameter 추가/수정

```bash
# 파라미터 생성
aws ssm put-parameter \
  --name "/community/NEW_PARAM" \
  --value "param_value" \
  --type SecureString \
  --region ap-northeast-2

# 파라미터 수정
aws ssm put-parameter \
  --name "/community/EXISTING_PARAM" \
  --value "new_value" \
  --type SecureString \
  --overwrite \
  --region ap-northeast-2

# 파라미터 조회
aws ssm get-parameter \
  --name "/community/PARAM_NAME" \
  --with-decryption \
  --region ap-northeast-2
```

**주의사항**:
- 새 파라미터 추가 시 `Jenkinsfile.backend`의 `get-parameters` 명령어에도 추가 필요
- SecureString 타입은 `--with-decryption` 필수

---

## 트러블슈팅

### 1. GitHub Actions 빌드 실패

#### 에러: `Credentials could not be loaded`

**원인**: OIDC 권한 누락

**해결**:
```yaml
permissions:
  id-token: write  # 추가
  contents: read
```

#### 에러: `repository does not exist`

**원인**: ECR 리포지토리 경로 오류

**확인**:
```bash
# SECRET 확인
REGISTRY_URL_ECR = 557690602093.dkr.ecr.ap-northeast-2.amazonaws.com  # ✅ 올바름
REGISTRY_URL_ECR = 557690602093.dkr.ecr.ap-northeast-2.amazonaws.com/ktb-personal  # ❌ 잘못됨
```

### 2. Jenkins 배포 실패

#### 에러: `백엔드 타겟그룹에 등록된 인스턴스가 없습니다`

**원인**: Target Group에 Healthy 인스턴스 없음

**해결**:
```bash
# ALB Target Group 상태 확인
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:ap-northeast-2:557690602093:targetgroup/ktb-persoanl-project-be/73713cba8e97578c

# EC2 인스턴스 상태 확인
aws ec2 describe-instances --instance-ids i-xxx --query 'Reservations[].Instances[].State.Name'
```

#### 에러: `SSM에서 필요한 백엔드 파라미터를 모두 가져오지 못했습니다`

**원인**: SSM Parameter 누락 또는 권한 부족

**해결**:
```bash
# 파라미터 존재 확인
aws ssm get-parameters \
  --names "/community/DB_URL" "/community/JWT_SECRET" \
  --region ap-northeast-2

# Jenkins IAM Role에 SSM 읽기 권한 확인
{
  "Effect": "Allow",
  "Action": [
    "ssm:GetParameter",
    "ssm:GetParameters"
  ],
  "Resource": "arn:aws:ssm:ap-northeast-2:557690602093:parameter/community/*"
}
```

### 3. EC2 배포 스크립트 실패

#### 에러: `pull access denied`

**원인**: ECR 인증 실패

**해결**:
```bash
# EC2에서 수동 ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 557690602093.dkr.ecr.ap-northeast-2.amazonaws.com

# IAM Instance Profile 확인
aws iam get-instance-profile --instance-profile-name <PROFILE_NAME>
```

#### 에러: `Health check failed`

**원인**: Spring Boot 기동 실패 또는 `/health` 엔드포인트 오류

**해결**:
```bash
# 컨테이너 로그 확인
docker compose logs backend

# 애플리케이션 로그 확인
docker exec -it <CONTAINER_ID> cat /app/logs/application.log

# 헬스 체크 수동 실행
curl http://localhost:8080/health
```

### 4. Docker 이미지 문제

#### 이미지 크기 최적화

**현재 문제**: 이미지 크기 과다 (500MB+)

**해결 방안**:
```dockerfile
# Dockerfile 개선 (Multi-stage build)
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN ./gradlew bootJar

FROM eclipse-temurin:21-jre-alpine  # JRE만 사용
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**효과**: 이미지 크기 70% 감소 (500MB → 150MB)

---

## 참고 자료

**관련 문서**:
- `@docs/deployment/README.md` - EC2 수동 배포 가이드
- `@docs/be/LLD.md` - 시스템 아키텍처 (Section 2)
- `.github/workflows/cd.yml` - GitHub Actions 워크플로우
- `Jenkinsfile.backend` - Jenkins 파이프라인 정의
- `docker-compose.yml` - Docker Compose 설정

**AWS 리소스**:
- ECR 리포지토리: `557690602093.dkr.ecr.ap-northeast-2.amazonaws.com/ktb-personal`
- Target Group ARN: `arn:aws:elasticloadbalancing:ap-northeast-2:557690602093:targetgroup/ktb-persoanl-project-be/73713cba8e97578c`
- SSM Parameter Prefix: `/community/`

---

**마지막 업데이트**: 2025-12-08
**작성자**: Claude Code
**버전**: 1.0
