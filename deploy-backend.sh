#!/usr/bin/env bash
set -euo pipefail

echo "[backend] 배포 시작"

: "${REGISTRY:?REGISTRY is required}"
: "${IMAGE_NAME:?IMAGE_NAME is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${JWT_SECRET:?JWT_SECRET is required}"
: "${AWS_S3_BUCKET:?AWS_S3_BUCKET is required}"
: "${AWS_REGION:?AWS_REGION is required}"
: "${FRONTEND_URL:?FRONTEND_URL is required}"

echo "[backend] REGISTRY=${REGISTRY}"
echo "[backend] IMAGE_NAME=${IMAGE_NAME}"
echo "[backend] IMAGE_TAG=${IMAGE_TAG}"
echo "[backend] DB_URL=${DB_URL}"
echo "[backend] FRONTEND_URL=${FRONTEND_URL}"

AWS_REGION_LOCAL="${AWS_REGION}"

echo "[backend] ECR 로그인 중..."
aws ecr get-login-password --region "${AWS_REGION_LOCAL}" \
  | sudo docker login --username AWS --password-stdin "${REGISTRY}"

echo "[backend] 기존 컨테이너 정리 중..."
sudo docker stop community-be 2>/dev/null || true
sudo docker rm   community-be 2>/dev/null || true

echo "[backend] 새 이미지 pull 중..."
sudo docker pull "${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"

echo "[backend] 새 컨테이너 실행 중..."
sudo docker run -d --name community-be \
  -p 8080:8080 \
  -e DB_URL="${DB_URL}" \
  -e DB_USERNAME="${DB_USERNAME}" \
  -e DB_PASSWORD="${DB_PASSWORD}" \
  -e JWT_SECRET="${JWT_SECRET}" \
  -e AWS_S3_BUCKET="${AWS_S3_BUCKET}" \
  -e AWS_REGION="${AWS_REGION}" \
  -e FRONTEND_URL="${FRONTEND_URL}" \
  --restart unless-stopped \
  --memory="1g" \
  "${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"

echo "[backend] 헬스체크 중..."
HEALTH_OK=false
for i in $(seq 1 180); do
  if curl -sf http://localhost:8080/health >/dev/null 2>&1; then
    echo "[backend] 헬스체크 성공! (${i}초)"
    HEALTH_OK=true
    break
  fi
  sleep 1
done

if [ "${HEALTH_OK}" = true ]; then
  echo "[backend] 배포 완료 ✅"
  exit 0
else
  echo "[backend] 헬스체크 실패 ❌"
  sudo docker logs --tail 50 community-be || true
  exit 1
fi