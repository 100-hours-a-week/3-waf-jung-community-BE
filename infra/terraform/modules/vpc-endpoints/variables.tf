variable "project" {
  type = string
}

variable "environment" {
  type = string
}

variable "region" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "vpc_cidr" {
  type = string
}

variable "app_subnet_ids" {
  type = list(string)
}

variable "private_route_table_id" {
  type = string
}
