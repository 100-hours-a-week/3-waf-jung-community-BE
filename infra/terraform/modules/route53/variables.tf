variable "environment" {
  type = string
}

variable "domain_name" {
  type = string
}

variable "subdomain" {
  type    = string
  default = "community"
}

variable "create_zone" {
  type    = bool
  default = false
}

variable "existing_zone_id" {
  type    = string
  default = ""
}

variable "alb_dns_name" {
  type = string
}

variable "alb_zone_id" {
  type = string
}
