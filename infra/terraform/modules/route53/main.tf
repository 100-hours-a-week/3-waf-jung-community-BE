resource "aws_route53_zone" "main" {
  count = var.create_zone ? 1 : 0
  name  = var.domain_name

  tags = {
    Environment = var.environment
  }
}

locals {
  zone_id = var.create_zone ? aws_route53_zone.main[0].zone_id : var.existing_zone_id
}

resource "aws_route53_record" "alb" {
  zone_id = local.zone_id
  name    = var.subdomain != "" ? "${var.subdomain}.${var.domain_name}" : var.domain_name
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}
