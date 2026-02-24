variable "environment" {
  type = string
}

variable "region" {
  type = string
}

variable "s3_bucket_name" {
  type = string
}

variable "frontend_url" {
  type = string
}

variable "db_url" {
  type      = string
  sensitive = true
}

variable "db_username" {
  type      = string
  sensitive = true
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "db_readonly_url" {
  type      = string
  sensitive = true
}

variable "jwt_secret" {
  type      = string
  sensitive = true
}
