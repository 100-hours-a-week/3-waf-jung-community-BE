#!/bin/bash
set -euxo pipefail
exec > /var/log/user-data.log 2>&1

echo "=== FE User Data Start ==="

REGION="${region}"
SSM_PREFIX="${ssm_prefix}"
ECR_REGISTRY="${ecr_registry}"
IMAGE_NAME="${image_name}"

# Read image tag from SSM
IMAGE_TAG=$(aws ssm get-parameter --name "$SSM_PREFIX/FE_IMAGE_TAG" --region "$REGION" --query "Parameter.Value" --output text)

# ECR Login
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# Pull image
FULL_IMAGE="$ECR_REGISTRY/$IMAGE_NAME:$IMAGE_TAG"
docker pull "$FULL_IMAGE"

# Run container
# BACKEND_URL='' -> same domain relative path (ALB routes /api/v1/* to BE)
docker run -d \
  --name community-fe \
  --restart unless-stopped \
  -p 3000:3000 \
  -e BACKEND_URL="" \
  -e API_PREFIX="/api/v1" \
  -e PORT=3000 \
  "$FULL_IMAGE"

# Wait for health check (max 60s)
echo "Waiting for FE health check..."
for i in $(seq 1 12); do
  if curl -sf http://localhost:3000/ > /dev/null 2>&1; then
    echo "FE is healthy!"
    break
  fi
  if [ "$i" -eq 12 ]; then
    echo "FE health check failed after 60s"
    docker logs community-fe
    exit 1
  fi
  sleep 5
done

# Start CloudWatch Agent
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s

# Setup docker log forwarding
mkdir -p /var/log/app
docker logs -f community-fe > /var/log/app/frontend.log 2>&1 &

echo "=== FE User Data Complete ==="
