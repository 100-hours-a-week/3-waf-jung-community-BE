#!/bin/bash
set -euxo pipefail
exec > /var/log/user-data.log 2>&1

echo "=== BE User Data Start ==="

REGION="${region}"
SSM_PREFIX="${ssm_prefix}"
ECR_REGISTRY="${ecr_registry}"
IMAGE_NAME="${image_name}"

# Start CloudWatch Agent FIRST (for diagnostic logs even if Docker fails)
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s
mkdir -p /var/log/app

# Read image tag from SSM
IMAGE_TAG=$(aws ssm get-parameter --name "$SSM_PREFIX/BE_IMAGE_TAG" --region "$REGION" --query "Parameter.Value" --output text)

# Read environment variables from SSM
DB_URL=$(aws ssm get-parameter --name "$SSM_PREFIX/DB_URL" --region "$REGION" --with-decryption --query "Parameter.Value" --output text)
DB_READONLY_URL=$(aws ssm get-parameter --name "$SSM_PREFIX/DB_READONLY_URL" --region "$REGION" --with-decryption --query "Parameter.Value" --output text)
DB_USERNAME=$(aws ssm get-parameter --name "$SSM_PREFIX/DB_USERNAME" --region "$REGION" --with-decryption --query "Parameter.Value" --output text)
DB_PASSWORD=$(aws ssm get-parameter --name "$SSM_PREFIX/DB_PASSWORD" --region "$REGION" --with-decryption --query "Parameter.Value" --output text)
JWT_SECRET=$(aws ssm get-parameter --name "$SSM_PREFIX/JWT_SECRET" --region "$REGION" --with-decryption --query "Parameter.Value" --output text)
AWS_S3_BUCKET=$(aws ssm get-parameter --name "$SSM_PREFIX/AWS_S3_BUCKET" --region "$REGION" --query "Parameter.Value" --output text)
FRONTEND_URL=$(aws ssm get-parameter --name "$SSM_PREFIX/FRONTEND_URL" --region "$REGION" --query "Parameter.Value" --output text)

# ECR Login
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# Pull image
FULL_IMAGE="$ECR_REGISTRY/$IMAGE_NAME:$IMAGE_TAG"
docker pull "$FULL_IMAGE"

# Run container with tuning parameters
docker run -d \
  --name community-be \
  --restart unless-stopped \
  --memory=2g \
  -p 8080:8080 \
  -e DB_URL="$DB_URL" \
  -e DB_READONLY_URL="$DB_READONLY_URL" \
  -e DB_USERNAME="$DB_USERNAME" \
  -e DB_PASSWORD="$DB_PASSWORD" \
  -e JWT_SECRET="$JWT_SECRET" \
  -e AWS_S3_BUCKET="$AWS_S3_BUCKET" \
  -e AWS_REGION="$REGION" \
  -e FRONTEND_URL="$FRONTEND_URL" \
  -e HIKARI_MAX_POOL_SIZE=30 \
  -e HIKARI_MIN_IDLE=10 \
  -e JAVA_TOOL_OPTIONS="-Xms1g -Xmx1500m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
  -e LOGGING_LEVEL_ORG_HIBERNATE_SQL=WARN \
  -e SERVER_CONTEXT_PATH=/api/v1 \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
  "$FULL_IMAGE"

# Start docker log forwarding immediately (for CloudWatch)
docker logs -f community-be > /var/log/app/backend.log 2>&1 &

# Wait for health check (max 180s)
echo "Waiting for BE health check..."
for i in $(seq 1 36); do
  if curl -sf http://localhost:8080/api/v1/health > /dev/null 2>&1; then
    echo "BE is healthy!"
    break
  fi
  if [ "$i" -eq 36 ]; then
    echo "BE health check failed after 180s"
    echo "=== Docker logs ==="
    docker logs --tail 200 community-be
    echo "=== Docker inspect ==="
    docker inspect community-be --format='{{.State.Status}} ExitCode={{.State.ExitCode}}'
    exit 1
  fi
  sleep 5
done

# === Schema Fix: Ensure DEFAULT values on columns Hibernate doesn't manage ===
(
  set +e
  DB_HOST=$(echo "$DB_URL" | sed -n 's|jdbc:mysql://\([^:]*\):.*|\1|p')
  echo "Fixing post_stats.last_updated DEFAULT value..."
  mysql -h "$DB_HOST" -u "$DB_USERNAME" -p"$DB_PASSWORD" community -e \
    "ALTER TABLE post_stats MODIFY last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;" 2>/dev/null \
    && echo "Schema fix applied." || echo "WARN: Schema fix skipped (table may not exist yet)."
) || true

# === One-time Data Seeding (non-fatal) ===
# Wrapped in subshell so seeding failures don't prevent instance health check
(
  set +e  # Disable exit-on-error for seeding section
  echo "Checking if data seeding is needed..."

  # Extract DB host from JDBC URL
  DB_HOST=$(echo "$DB_URL" | sed -n 's|jdbc:mysql://\([^:]*\):.*|\1|p')

  # Check if users table is empty
  USER_COUNT=$(mysql -h "$DB_HOST" -u "$DB_USERNAME" -p"$DB_PASSWORD" community -N -e "SELECT COUNT(*) FROM users;" 2>/dev/null || echo "0")

  if [ "$USER_COUNT" -eq 0 ]; then
    echo "Database is empty. Starting data seeding..."

    # Download seed SQL from S3
    if ! aws s3 cp "s3://$AWS_S3_BUCKET/seed/03_insert_dummy_small.sql" /tmp/seed.sql --region "$REGION"; then
      echo "WARN: Seed SQL not found in S3. Skipping seeding."
      exit 0
    fi

    # Execute seed SQL
    if ! mysql -h "$DB_HOST" -u "$DB_USERNAME" -p"$DB_PASSWORD" community < /tmp/seed.sql; then
      echo "WARN: Seed SQL execution failed. Skipping seeding."
      rm -f /tmp/seed.sql
      exit 0
    fi
    echo "Seed data inserted."

    # Generate post_stats entries for all posts
    echo "Generating post_stats..."
    mysql -h "$DB_HOST" -u "$DB_USERNAME" -p"$DB_PASSWORD" community -e "
      INSERT INTO post_stats (post_id, like_count, comment_count, view_count, last_updated)
      SELECT p.post_id,
             COALESCE(l.cnt, 0),
             COALESCE(c.cnt, 0),
             FLOOR(RAND() * 500),
             NOW()
      FROM posts p
      LEFT JOIN (SELECT post_id, COUNT(*) AS cnt FROM post_likes GROUP BY post_id) l ON p.post_id = l.post_id
      LEFT JOIN (SELECT post_id, COUNT(*) AS cnt FROM comments WHERE comment_status = 'ACTIVE' GROUP BY post_id) c ON p.post_id = c.post_id
      ON DUPLICATE KEY UPDATE post_id = p.post_id;
    " || echo "WARN: post_stats generation failed (non-fatal)."
    echo "post_stats generated."

    # Verify
    mysql -h "$DB_HOST" -u "$DB_USERNAME" -p"$DB_PASSWORD" community -e "
      SELECT 'users' AS tbl, COUNT(*) AS cnt FROM users
      UNION ALL SELECT 'posts', COUNT(*) FROM posts
      UNION ALL SELECT 'comments', COUNT(*) FROM comments
      UNION ALL SELECT 'post_likes', COUNT(*) FROM post_likes
      UNION ALL SELECT 'post_stats', COUNT(*) FROM post_stats;
    " || echo "WARN: Seed verification query failed (non-fatal)."

    # Cleanup
    rm -f /tmp/seed.sql
    echo "Data seeding complete!"
  else
    echo "Database already has $USER_COUNT users. Skipping seed."
  fi
) || echo "WARN: Data seeding failed (non-fatal). Instance will continue."

echo "=== BE User Data Complete ==="
