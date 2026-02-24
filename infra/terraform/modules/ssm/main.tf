locals {
  string_params = {
    "AWS_S3_BUCKET" = var.s3_bucket_name
    "AWS_REGION"    = var.region
    "FRONTEND_URL"  = var.frontend_url
    "BE_IMAGE_TAG"  = "be-latest"
    "FE_IMAGE_TAG"  = "fe-latest"
  }

  secure_params = {
    "DB_URL"          = var.db_url
    "DB_USERNAME"     = var.db_username
    "DB_PASSWORD"     = var.db_password
    "DB_READONLY_URL" = var.db_readonly_url
    "JWT_SECRET"      = var.jwt_secret
  }
}

resource "aws_ssm_parameter" "string" {
  for_each = local.string_params

  name  = "/community/${var.environment}/${each.key}"
  type  = "String"
  value = each.value

  tags = {
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "secure" {
  for_each = local.secure_params

  name  = "/community/${var.environment}/${each.key}"
  type  = "SecureString"
  value = each.value

  tags = {
    Environment = var.environment
  }

  lifecycle {
    ignore_changes = [value]
  }
}
