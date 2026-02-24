# DC2 Community Platform — 부하테스트 인프라 구축 설명서

## 1. 개요

### 목적
블로그 시리즈 "백만 MAU 트래픽 테스트"를 위한 부하테스트 환경 구축.
프로젝트 종료 후 정리된 AWS 인프라를 **Terraform/Packer IaC**로 완전히 재구축하고,
**ASG 기반 무중단 배포 + K6 부하테스트**로 시스템 한계를 검증한다.

### 트래픽 목표
| 지표 | 값 |
|------|-----|
| MAU | 1,000,000 |
| DAU (MAU의 10%) | 100,000 |
| Avg RPS | ~307 |
| Peak RPS (1.5×) | ~480 |
| Avg QPS | ~400 |
| Peak QPS | ~700 |

### 기술 스택
| 계층 | 기술 |
|------|------|
| Backend | Spring Boot 3.5.6, Java 24, Gradle |
| Frontend | React (Vite) + Express.js static server |
| Database | MySQL 8.0 (RDS Primary + Read Replica) |
| Infra | AWS (VPC, ALB, ASG, RDS, S3, Route53, SSM) |
| IaC | Terraform 1.5+, Packer 1.9+ |
| CI/CD | GitHub Actions → ECR → ASG Instance Refresh |
| 부하테스트 | K6 (로컬 Mac → HTTPS ALB) |
| 모니터링 | CloudWatch (Agent + Logs) |

---

## 2. 아키텍처 다이어그램

```
                        ┌─────────────────────────────────────────────┐
                        │              AWS ap-northeast-2             │
                        │                                             │
  K6 (Local Mac)        │   ┌─────────────┐                          │
       │                │   │  Route 53    │                          │
       │ HTTPS          │   │  *.ktb-waf   │                          │
       ▼                │   │   .cloud     │                          │
  ┌─────────┐           │   └──────┬──────┘                          │
  │ Internet│───────────┼──────────▼──────────────────────────────┐   │
  └─────────┘           │   ┌─────────────────────┐               │   │
                        │   │    ALB (HTTPS/443)   │  Public       │   │
                        │   │  ACM: *.ktb-waf.cloud│  Subnets     │   │
                        │   │                     │  10.0.1.0/24  │   │
                        │   │  /api/v1/* → BE TG  │  10.0.2.0/24  │   │
                        │   │  /*        → FE TG  │               │   │
                        │   └───┬──────────┬──────┘               │   │
                        │       │          │                       │   │
                        │   ┌───▼───┐  ┌───▼───┐   App Subnets    │   │
                        │   │BE ASG │  │FE ASG │   10.0.11.0/24   │   │
                        │   │1~4×   │  │ 2×    │   10.0.12.0/24   │   │
                        │   │t3.med │  │t3.micr│                  │   │
                        │   │ Spot  │  │ Spot  │                  │   │
                        │   │:8080  │  │:3000  │                  │   │
                        │   └───┬───┘  └───────┘                  │   │
                        │       │                                  │   │
                        │   ┌───▼─────────────┐   Data Subnets    │   │
                        │   │    RDS MySQL     │   10.0.21.0/24   │   │
                        │   │  Primary         │   10.0.22.0/24   │   │
                        │   │  db.t3.medium    │                  │   │
                        │   │       │          │                  │   │
                        │   │  Read Replica    │                  │   │
                        │   │  db.t3.small     │                  │   │
                        │   └─────────────────┘                  │   │
                        │                                         │   │
                        │   ┌──────────────────────────────────┐  │   │
                        │   │   VPC Endpoints (NAT 대체)       │  │   │
                        │   │   ecr.dkr / ecr.api / s3        │  │   │
                        │   │   ssm / ssmmessages / ec2messages│  │   │
                        │   │   logs (CloudWatch)              │  │   │
                        │   └──────────────────────────────────┘  │   │
                        │                                         │   │
                        │   ┌──────────┐  ┌─────────┐            │   │
                        │   │   ECR    │  │   S3    │            │   │
                        │   │ ktb-     │  │ images  │            │   │
                        │   │ personal │  │ + logs  │            │   │
                        │   └──────────┘  └─────────┘            │   │
                        └─────────────────────────────────────────────┘
```

### Docker 이미지 Pull 경로
```
ECR (ktb-personal) ──VPC Endpoint(ecr.dkr)──> EC2 Instance ──docker run──> Container
                                  │
                        S3 Gateway Endpoint (이미지 레이어)
```

---

## 3. IaC 구조

### 디렉토리 트리
```
infra/
├── packer/
│   ├── community-ami.pkr.hcl      # AMI 정의 (FE/BE 공용)
│   ├── variables.pkr.hcl          # 변수
│   └── scripts/
│       └── setup.sh               # Docker, CW Agent, 도구 설치
│
└── terraform/
    ├── environments/
    │   └── loadtest/
    │       ├── main.tf             # 모듈 조합 + Billing Alarm
    │       ├── variables.tf        # 입력 변수 정의
    │       ├── outputs.tf          # 출력값
    │       ├── backend.tf          # S3 원격 상태 저장소
    │       ├── providers.tf        # AWS Provider
    │       ├── versions.tf         # 버전 제약
    │       ├── terraform.tfvars    # 실제 값 (gitignored)
    │       └── terraform.tfvars.example
    │
    └── modules/
        ├── vpc/                    # VPC, 서브넷, IGW, 라우트
        ├── vpc-endpoints/          # 7개 VPC Endpoint (NAT 대체)
        ├── security-groups/        # ALB, BE, FE, RDS, VPCE SG
        ├── iam/                    # EC2 Instance Profile
        ├── alb/                    # ALB, TG, Listeners, ACM
        ├── asg/                    # BE/FE ASG, Launch Template
        │   └── templates/
        │       ├── be-user-data.sh.tpl
        │       └── fe-user-data.sh.tpl
        ├── rds/                    # Primary + Replica, Parameter Group
        ├── ssm/                    # SSM Parameters (10개)
        ├── route53/                # Hosted Zone, A Record
        └── s3/                     # 이미지 버킷 + ALB 로그 버킷
```

### 10개 모듈 역할
| 모듈 | 역할 | 주요 리소스 수 |
|------|------|-------------|
| vpc | 네트워크 기반 | VPC, 6 Subnets, IGW, 2 Route Tables |
| vpc-endpoints | AWS 서비스 접근 (NAT 대체) | 7 Endpoints + SG |
| security-groups | 네트워크 접근 제어 | 5 Security Groups |
| iam | 권한 관리 | Role, 4 Policies, Instance Profile |
| alb | 로드 밸런싱 + SSL | ALB, 2 TGs, 2 Listeners, ACM |
| asg | 오토스케일링 + 배포 | 2 ASGs, 2 Launch Templates, 2 Scaling Policies |
| rds | 데이터베이스 | Primary, Replica, Parameter Group, Subnet Group |
| ssm | 설정 관리 | 10 Parameters (5 SecureString) |
| route53 | DNS | Hosted Zone, A Record |
| s3 | 오브젝트 스토리지 | 2 Buckets, Seed SQL Object |

### 변수 관리
- `terraform.tfvars`: 실제 비밀 값 (DB 비밀번호, JWT Secret 등) → `.gitignore`
- `terraform.tfvars.example`: 구조 예시 (값 마스킹) → Git 추적
- 변수 우선순위: tfvars > 환경변수 > default 값

---

## 4. 네트워크 설계

### VPC CIDR 할당
```
VPC: 10.0.0.0/16 (65,536 IPs)

Public Subnets (ALB):
  10.0.1.0/24  — ap-northeast-2a (254 IPs)
  10.0.2.0/24  — ap-northeast-2c (254 IPs)

App Subnets (EC2 BE + FE):
  10.0.11.0/24 — ap-northeast-2a
  10.0.12.0/24 — ap-northeast-2c

Data Subnets (RDS):
  10.0.21.0/24 — ap-northeast-2a
  10.0.22.0/24 — ap-northeast-2c
```

### NAT Gateway를 VPC Endpoints로 대체한 이유
| 항목 | NAT Gateway | VPC Endpoints |
|------|-------------|---------------|
| 월간 비용 | ~$45 + 데이터 전송비 | ~$43 (Interface × 6) |
| S3 접근 | 유료 | **무료** (Gateway) |
| 데이터 전송비 | $0.045/GB | 없음 |
| 보안 | 모든 트래픽 NAT 경유 | 필요한 서비스만 허용 |
| 장애 범위 | NAT 장애 → 전체 장애 | 개별 서비스 독립 |

**결론**: 비용은 비슷하지만, S3 Gateway가 무료이고 보안 측면에서 필요한 서비스만 노출하므로 VPC Endpoints 선택.

### VPC Endpoints 상세
| Endpoint | Type | 비용/월 | 용도 |
|----------|------|---------|------|
| `ecr.dkr` | Interface | ~$7.20 | Docker 이미지 Pull |
| `ecr.api` | Interface | ~$7.20 | ECR API 호출 |
| `s3` | Gateway | **무료** | S3 접근 (이미지, 로그, Seed SQL) |
| `ssm` | Interface | ~$7.20 | 파라미터 읽기 |
| `ssmmessages` | Interface | ~$7.20 | Session Manager |
| `ec2messages` | Interface | ~$7.20 | SSM Agent 통신 |
| `logs` | Interface | ~$7.20 | CloudWatch 로그 전송 |

---

## 5. 컴퓨팅 설계

### BE ASG (Backend)
| 항목 | 값 |
|------|-----|
| Instance Types | t3.medium, t3a.medium, t2.medium (Spot) |
| vCPU / RAM | 2 vCPU / 4 GB |
| Min / Max / Desired | 1 / 4 / 2 |
| Health Check | ELB (ALB Target Group) |
| Grace Period | 180초 |
| Scaling Policy 1 | CPU 60% Target Tracking |
| Scaling Policy 2 | ALB 200 req/target/min |

### FE ASG (Frontend)
| 항목 | 값 |
|------|-----|
| Instance Types | t3.micro, t3a.micro (Spot) |
| vCPU / RAM | 2 vCPU / 1 GB |
| Min / Max / Desired | 2 / 2 / 2 (고정) |
| Health Check | ELB |
| Grace Period | 60초 |

### Launch Template User-Data 흐름

**BE (be-user-data.sh.tpl)**:
```
1. CloudWatch Agent 시작 (최상단 — 로그 수집 우선)
2. SSM에서 IMAGE_TAG + 환경변수 읽기 (VPC Endpoint 경유)
3. ECR 로그인 + Docker Pull
4. docker run (HikariCP 튜닝 + JVM 옵션 포함)
5. Docker 로그 → /var/log/app/backend.log 포워딩
6. /api/v1/health 헬스체크 대기 (최대 180초)
7. Schema Fix: ALTER TABLE post_stats DEFAULT 값 보정 (non-fatal)
8. 데이터 시딩: S3에서 seed SQL 다운로드 → 실행 (non-fatal subshell)
```

**Docker 실행 옵션**:
```bash
docker run -d \
  --memory=2g \
  -e SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30 \
  -e SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=10 \
  -e JAVA_TOOL_OPTIONS="-Xms1g -Xmx1500m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
  -e LOGGING_LEVEL_ORG_HIBERNATE_SQL=WARN \
  ...
```

**FE (fe-user-data.sh.tpl)**:
```
1. CloudWatch Agent 시작
2. SSM에서 FE_IMAGE_TAG 읽기
3. ECR 로그인 + Docker Pull
4. docker run (BACKEND_URL='', API_PREFIX='/api/v1', PORT=3000)
5. / 헬스체크 대기
```

---

## 6. 데이터베이스 설계

### RDS 구성
| 항목 | Primary | Replica |
|------|---------|---------|
| Instance Class | db.t3.medium (2 vCPU, 4 GB) | db.t3.small (2 vCPU, 2 GB) |
| Engine | MySQL 8.0 | MySQL 8.0 |
| Storage | 20 GB gp3, 자동 확장 100 GB | Primary와 동기화 |
| Multi-AZ | No | No (같은 AZ, 비용 절감) |
| Backup | 1일 보존 | — |
| Performance Insights | 7일 (Free Tier) | 비활성 |
| Deletion Protection | No | No |
| Skip Final Snapshot | Yes | Yes |

### Parameter Group 튜닝
| 파라미터 | 값 | 이유 |
|----------|-----|------|
| max_connections | 300 | HikariCP 30 × 4 인스턴스 + 여유 |
| innodb_flush_log_at_trx_commit | 2 | 쓰기 성능 향상 (부하테스트용) |
| innodb_log_buffer_size | 64 MB | 대량 쓰기 버퍼링 |
| slow_query_log | ON | 1초 이상 쿼리 감지 |
| character_set_server | utf8mb4 | 한글 + 이모지 지원 |

### AbstractRoutingDataSource (Read/Write 분리)

```java
// DataSourceConfig.java
@Primary DataSource → RoutingDataSource → LazyConnectionDataSourceProxy
  ├── "primary" → HikariDataSource (DB_URL → Primary endpoint)
  └── "replica" → HikariDataSource (DB_READONLY_URL → Replica endpoint)

// RoutingDataSource.java
determineCurrentLookupKey():
  if TransactionSynchronizationManager.isCurrentTransactionReadOnly()
    → return "replica"
  else
    → return "primary"
```

**Fallback**: `DB_READONLY_URL`이 미설정 시 Primary만 사용 (로컬 개발 호환)

**기존 readOnly 트랜잭션 활용**:
- `PostService` (게시글 목록/상세), `UserService` (사용자 조회)
- `CommentService` (댓글 목록), `AuthService` (토큰 검증)
- `LikeService` (좋아요 상태 조회)

### 자동 시딩
- Terraform이 S3에 seed SQL 업로드 (`aws_s3_object`)
- user-data에서 DB가 비어있으면 S3에서 다운로드 후 실행
- post_stats 자동 생성 (LEFT JOIN으로 좋아요/댓글 수 집계)
- 시딩 실패해도 인스턴스 정상 부팅 (non-fatal subshell)

---

## 7. 배포 파이프라인

### GitHub Actions → ASG Instance Refresh

```
main push
    │
    ▼
┌─────────┐    ┌─────────┐    ┌──────────────┐
│  Test    │───▶│  Build  │───▶│   Deploy     │
│ Gradle  │    │ Docker  │    │ SSM Update   │
│ build   │    │ ECR Push│    │ Instance     │
│ test    │    │         │    │ Refresh      │
└─────────┘    └─────────┘    └──────────────┘
```

### Jenkins 제거 배경
- 기존: GitHub Actions → ECR Push → Jenkins Webhook → Jenkins SSH 배포
- 문제: Jenkins EC2 인스턴스 비용, 단일 장애점, SSH 키 관리
- 현재: GitHub Actions → ECR Push → SSM Parameter 업데이트 → ASG Instance Refresh
- 장점: 서버리스, 무중단 배포 네이티브, IAM 기반 인증

### OIDC 인증
- GitHub Actions에서 AWS 임시 자격 증명 획득 (Access Key 불필요)
- Trust Policy: `repo:WAFriend3416/*` 와일드카드
- IAM Role: ECR PowerUser + SSM Read/Write + ASG Instance Refresh

### Instance Refresh 설정
| 항목 | 값 |
|------|-----|
| MinHealthyPercentage | 50% |
| MaxHealthyPercentage | 200% |
| InstanceWarmup (BE) | 180초 |
| InstanceWarmup (FE) | 60초 |
| Strategy | Rolling |

---

## 8. 멱등성 보장

`terraform destroy` → `terraform apply`만으로 인프라 + 데이터가 완전히 재현된다.

### 8.1 S3 Seed SQL (Terraform 관리)
```terraform
resource "aws_s3_object" "seed_sql" {
  bucket = aws_s3_bucket.images.id
  key    = "seed/03_insert_dummy_small.sql"
  source = var.seed_sql_path
  etag   = filemd5(var.seed_sql_path)
}
```
- destroy 시 S3 버킷과 함께 삭제 → apply 시 자동 재업로드

### 8.2 Non-Fatal 시딩 (user-data)
```bash
# 서브셸로 격리 — 실패해도 인스턴스 정상 부팅
(
  set +e
  USER_COUNT=$(mysql ... "SELECT COUNT(*) FROM users;" || echo "0")
  if [ "$USER_COUNT" -eq 0 ]; then
    aws s3 cp ... && mysql < /tmp/seed.sql
  fi
) || echo "WARN: seeding failed (non-fatal)"
```

### 8.3 S3 force_destroy
```terraform
resource "aws_s3_bucket" "images" {
  force_destroy = true  # destroy 시 버킷 내 객체 자동 삭제
}
```

### 8.4 Route53 NS 위임 (유일한 수동 작업)
destroy 후 새 Hosted Zone 생성 시 NS 레코드 변경됨:
```bash
terraform output route53_nameservers
# → 도메인 레지스트라(가비아)에서 수동 업데이트
# → DNS 전파 대기 (보통 10~30분)
```

### 재적용 체크리스트
```bash
1. terraform apply                              # ACM 검증에서 대기
2. terraform output route53_nameservers          # NS 확인
3. 가비아에서 NS 레코드 업데이트                    # 수동
4. DNS 전파 대기 → ACM 자동 검증 → apply 완료
5. curl https://community.ktb-waf.cloud/api/v1/health
```

---

## 9. 트러블슈팅 기록

### 문제 1: JwtAuthenticationFilter context-path
| 항목 | 내용 |
|------|------|
| **증상** | `/api/v1/health` 요청에 401 Unauthorized |
| **원인** | `getRequestURI()`가 context-path 포함 URI 반환 → PUBLIC_PATHS에 `/health` 매칭 실패 |
| **해결** | context-path(`/api/v1`)를 strip 후 매칭하도록 수정 |

### 문제 2: Docker 아키텍처 불일치
| 항목 | 내용 |
|------|------|
| **증상** | EC2에서 Docker 컨테이너 시작 실패 |
| **원인** | 로컬 빌드 환경 Apple Silicon (arm64) → EC2 t3 (amd64) |
| **해결** | `docker buildx --platform linux/amd64` 지정 |

### 문제 3: CloudWatch 로그 누락
| 항목 | 내용 |
|------|------|
| **증상** | user-data 실행 로그가 CloudWatch에 미전송 |
| **원인** | CW Agent가 Docker 시작 이후에 실행됨 |
| **해결** | CW Agent 시작을 user-data 최상단으로 이동 |

### 문제 4: SSM Agent 미등록
| 항목 | 내용 |
|------|------|
| **증상** | Session Manager에서 인스턴스 미표시 |
| **원인** | 미확인 (AMI에 Agent 포함되어 있으나 등록 지연 추정) |
| **해결** | S3 + user-data 시딩으로 SSM Session Manager 우회 |

### 문제 5: S3 버킷 삭제 실패
| 항목 | 내용 |
|------|------|
| **증상** | `terraform destroy` 시 S3 BucketNotEmpty 에러 |
| **원인** | `force_destroy = false` 기본값 |
| **해결** | `force_destroy = true` 설정 |

### 문제 6: Zero Date Value (Phase 5 발견)
| 항목 | 내용 |
|------|------|
| **증상** | 게시글 조회 500 에러 (`Zero date value prohibited`) |
| **원인** | Seed SQL에 zero date (`0000-00-00`) 포함 |
| **해결** | SSM DB_URL에 `&zeroDateTimeBehavior=convertToNull` 추가 |

### 문제 7: post_stats DEFAULT 누락 (Phase 5 발견)
| 항목 | 내용 |
|------|------|
| **증상** | 게시글 작성 500 에러 (`last_updated no default`) |
| **원인** | Hibernate `ddl-auto:update`가 DEFAULT 없이 컬럼 생성 |
| **해결** | user-data에서 ALTER TABLE 실행 (non-fatal subshell) |

### 문제 8: S3 Presigned URL ACL 거부 (Phase 5 발견)
| 항목 | 내용 |
|------|------|
| **증상** | S3 업로드 400 (`ACLs not supported`) 및 403 (`PutObjectAcl denied`) |
| **원인** | S3 기본값 ACL 비활성 + IAM에 PutObjectAcl 누락 |
| **해결** | `aws_s3_bucket_ownership_controls` + IAM 정책에 `s3:PutObjectAcl` 추가 |

---

## 10. 비용 추정

### 월간 비용 (Spot 기준, ap-northeast-2)
| 리소스 | 스펙 | 월간 비용 |
|--------|------|----------|
| EC2 BE × 2 | t3.medium Spot (~$0.013/h) | ~$19 |
| EC2 FE × 2 | t3.micro Spot (~$0.003/h) | ~$4 |
| RDS Primary | db.t3.medium | ~$52 |
| RDS Replica | db.t3.small | ~$26 |
| ALB | 기본 + LCU | ~$20 |
| VPC Endpoints × 6 | Interface | ~$43 |
| S3 | 이미지 + 로그 | ~$1 |
| Route53 | Hosted Zone + 쿼리 | ~$1 |
| CloudWatch | 메트릭 + 로그 | ~$5 |
| **총계** | | **~$171/월** |

### 비용 최적화
- **Spot 인스턴스**: On-Demand 대비 60~70% 절감
- **NAT Gateway 제거**: ~$45/월 절감
- **RDS Skip Final Snapshot**: 스냅샷 저장 비용 없음
- **CloudWatch 7일 보존**: 로그 저장 비용 최소화
- **Billing Alarm**: $20 threshold 설정

### 테스트 기간만 운영 시
부하테스트는 1~2일이면 충분 → 실제 비용: **~$10~15**
```bash
# 테스트 완료 후 즉시 정리
terraform destroy
```
