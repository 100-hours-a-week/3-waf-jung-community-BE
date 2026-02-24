variable "project" {
  type = string
}

variable "environment" {
  type = string
}

variable "image_bucket_name" {
  type = string
}

variable "domain_name" {
  type = string
}

variable "elb_account_id" {
  description = "ELB account ID for the region (ap-northeast-2 = 600734575887)"
  type        = string
  default     = "600734575887"
}

variable "seed_sql_path" {
  description = "Path to seed SQL file for automatic upload to S3 (empty to skip)"
  type        = string
  default     = ""
}
