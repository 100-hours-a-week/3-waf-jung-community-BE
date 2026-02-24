variable "project" {
  type    = string
  default = "community"
}

variable "environment" {
  type    = string
  default = "loadtest"
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "domain_name" {
  type    = string
  default = "ktb-waf.cloud"
}

variable "ami_id" {
  description = "Packer-built AMI ID"
  type        = string
}

variable "ecr_registry" {
  description = "ECR registry URL (account_id.dkr.ecr.region.amazonaws.com)"
  type        = string
}

variable "account_id" {
  description = "AWS Account ID"
  type        = string
}

variable "db_username" {
  type      = string
  sensitive = true
  default   = "admin"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "jwt_secret" {
  type      = string
  sensitive = true
}

variable "existing_route53_zone_id" {
  description = "Existing Route53 hosted zone ID (leave empty to create new)"
  type        = string
  default     = ""
}

variable "image_bucket_name" {
  type    = string
  default = "community-loadtest-images"
}
