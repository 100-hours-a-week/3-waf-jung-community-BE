variable "project" {
  type = string
}

variable "environment" {
  type = string
}

variable "data_subnet_ids" {
  type = list(string)
}

variable "rds_sg_id" {
  type = string
}

variable "db_name" {
  type    = string
  default = "community"
}

variable "db_username" {
  type      = string
  sensitive = true
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "primary_instance_class" {
  type    = string
  default = "db.t3.medium"
}

variable "replica_instance_class" {
  type    = string
  default = "db.t3.small"
}
