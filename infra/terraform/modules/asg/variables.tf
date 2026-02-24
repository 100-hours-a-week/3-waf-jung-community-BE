variable "project" {
  type = string
}

variable "environment" {
  type = string
}

variable "region" {
  type = string
}

variable "ami_id" {
  type = string
}

variable "instance_profile_name" {
  type = string
}

variable "app_subnet_ids" {
  type = list(string)
}

variable "be_sg_id" {
  type = string
}

variable "fe_sg_id" {
  type = string
}

variable "be_target_group_arn" {
  type = string
}

variable "fe_target_group_arn" {
  type = string
}

variable "ssm_prefix" {
  type = string
}

variable "ecr_registry" {
  type = string
}

variable "image_name" {
  type    = string
  default = "ktb-personal"
}

variable "alb_arn_suffix" {
  type = string
}

variable "be_target_group_arn_suffix" {
  type = string
}
