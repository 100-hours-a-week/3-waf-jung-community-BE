#!/bin/bash
# =============================================
# DB 초기화 통합 실행 스크립트
# =============================================
# 사용법:
#   ./run_all.sh                    # 전체 실행 (drop → create → data → verify)
#   ./run_all.sh --small            # 절반 크기 더미데이터 (2MB)
#   ./run_all.sh --skip-data        # 더미데이터 제외
#   ./run_all.sh --only-schema      # 스키마만 (drop → create)
#   ./run_all.sh --only-verify      # 검증만
# =============================================

set -e  # 에러 시 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 설정
CONTAINER_NAME="${MYSQL_CONTAINER:-mysql}"
DB_USER="${MYSQL_USER:-root}"
DB_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
DB_NAME="${MYSQL_DATABASE:-community}"

# 스크립트 디렉토리
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 헬퍼 함수
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# MySQL 실행 함수
run_sql() {
    local sql_file=$1
    local description=$2

    log_info "$description..."

    if [ -n "$DB_PASSWORD" ]; then
        docker exec -i "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < "$sql_file"
    else
        docker exec -i "$CONTAINER_NAME" mysql -u"$DB_USER" "$DB_NAME" < "$sql_file"
    fi

    if [ $? -eq 0 ]; then
        log_success "$description 완료"
    else
        log_error "$description 실패"
        exit 1
    fi
}

# 비밀번호 입력 (설정되지 않은 경우)
prompt_password() {
    if [ -z "$DB_PASSWORD" ]; then
        echo -n "MySQL root 비밀번호: "
        read -s DB_PASSWORD
        echo
        export MYSQL_ROOT_PASSWORD="$DB_PASSWORD"
    fi
}

# Docker 컨테이너 확인
check_container() {
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_error "MySQL 컨테이너 '$CONTAINER_NAME'가 실행 중이 아닙니다."
        echo "사용 가능한 컨테이너:"
        docker ps --format '  - {{.Names}}'
        exit 1
    fi
    log_success "MySQL 컨테이너 '$CONTAINER_NAME' 확인됨"
}

# 메인 실행
main() {
    echo "============================================="
    echo " KTB Community DB 초기화 스크립트"
    echo "============================================="
    echo ""

    # 옵션 파싱
    SKIP_DATA=false
    ONLY_SCHEMA=false
    ONLY_VERIFY=false
    USE_SMALL_DATA=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            --small)
                USE_SMALL_DATA=true
                shift
                ;;
            --skip-data)
                SKIP_DATA=true
                shift
                ;;
            --only-schema)
                ONLY_SCHEMA=true
                shift
                ;;
            --only-verify)
                ONLY_VERIFY=true
                shift
                ;;
            --container)
                CONTAINER_NAME="$2"
                shift 2
                ;;
            --password)
                DB_PASSWORD="$2"
                shift 2
                ;;
            -h|--help)
                echo "사용법: $0 [옵션]"
                echo ""
                echo "옵션:"
                echo "  --small          절반 크기 더미데이터 사용 (2MB, 600유저/1500게시글)"
                echo "  --skip-data      더미데이터 삽입 건너뛰기"
                echo "  --only-schema    스키마만 생성 (drop → create)"
                echo "  --only-verify    검증만 실행"
                echo "  --container NAME MySQL 컨테이너 이름 (기본: mysql)"
                echo "  --password PASS  MySQL root 비밀번호"
                echo "  -h, --help       도움말"
                echo ""
                echo "환경변수:"
                echo "  MYSQL_CONTAINER      컨테이너 이름"
                echo "  MYSQL_ROOT_PASSWORD  root 비밀번호"
                echo "  MYSQL_DATABASE       데이터베이스 이름 (기본: community)"
                echo ""
                echo "더미데이터 크기:"
                echo "  기본 (4.8MB): 1200유저, 3000게시글, ~40000댓글"
                echo "  --small (2MB): 600유저, 1500게시글, ~18000댓글"
                exit 0
                ;;
            *)
                log_error "알 수 없는 옵션: $1"
                exit 1
                ;;
        esac
    done

    # 사전 체크
    check_container
    prompt_password

    # 실행 시작
    START_TIME=$(date +%s)

    if [ "$ONLY_VERIFY" = true ]; then
        run_sql "$SCRIPT_DIR/04_verify.sql" "04. 데이터 검증"
    elif [ "$ONLY_SCHEMA" = true ]; then
        run_sql "$SCRIPT_DIR/01_drop_all.sql" "01. 테이블 삭제"
        run_sql "$SCRIPT_DIR/02_create_tables.sql" "02. 테이블 생성"
    else
        run_sql "$SCRIPT_DIR/01_drop_all.sql" "01. 테이블 삭제"
        run_sql "$SCRIPT_DIR/02_create_tables.sql" "02. 테이블 생성"

        if [ "$SKIP_DATA" = false ]; then
            if [ "$USE_SMALL_DATA" = true ]; then
                log_info "03. 더미 데이터 삽입... (Small: 2MB, 600유저/1500게시글)"
                run_sql "$SCRIPT_DIR/03_insert_dummy_small.sql" "03. 더미 데이터 삽입 (Small)"
            else
                log_info "03. 더미 데이터 삽입... (Full: 4.8MB, 1200유저/3000게시글)"
                run_sql "$SCRIPT_DIR/dummy_data.sql" "03. 더미 데이터 삽입 (Full)"
            fi
        else
            log_warn "03. 더미 데이터 삽입 건너뜀 (--skip-data)"
        fi

        run_sql "$SCRIPT_DIR/04_verify.sql" "04. 데이터 검증"
    fi

    # 완료
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))

    echo ""
    echo "============================================="
    log_success "DB 초기화 완료! (소요시간: ${DURATION}초)"
    echo "============================================="
}

main "$@"
