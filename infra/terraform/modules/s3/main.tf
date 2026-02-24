# Image Bucket
resource "aws_s3_bucket" "images" {
  bucket        = var.image_bucket_name
  force_destroy = true

  tags = {
    Name        = var.image_bucket_name
    Environment = var.environment
  }
}

resource "aws_s3_bucket_ownership_controls" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_public_access_block" "images" {
  bucket = aws_s3_bucket.images.id

  block_public_acls       = false
  block_public_policy     = true
  ignore_public_acls      = false
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "images" {
  bucket = aws_s3_bucket.images.id

  versioning_configuration {
    status = "Disabled"
  }
}

resource "aws_s3_bucket_cors_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT"]
    allowed_origins = ["https://${var.domain_name}", "https://community.${var.domain_name}", "*"]
    max_age_seconds = 3600
  }
}

# ALB Access Log Bucket
resource "aws_s3_bucket" "alb_logs" {
  bucket        = "${var.project}-${var.environment}-alb-logs"
  force_destroy = true

  tags = {
    Name        = "${var.project}-${var.environment}-alb-logs"
    Environment = var.environment
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "alb_logs" {
  bucket = aws_s3_bucket.alb_logs.id

  rule {
    id     = "expire-logs"
    status = "Enabled"

    filter {}

    expiration {
      days = 7
    }
  }
}

resource "aws_s3_bucket_policy" "alb_logs" {
  bucket = aws_s3_bucket.alb_logs.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::${var.elb_account_id}:root" }
        Action    = "s3:PutObject"
        Resource  = "${aws_s3_bucket.alb_logs.arn}/*"
      }
    ]
  })
}

# Seed SQL for data seeding (uploaded automatically on terraform apply)
resource "aws_s3_object" "seed_sql" {
  count  = var.seed_sql_path != "" ? 1 : 0
  bucket = aws_s3_bucket.images.id
  key    = "seed/03_insert_dummy_small.sql"
  source = var.seed_sql_path
  etag   = filemd5(var.seed_sql_path)
}
