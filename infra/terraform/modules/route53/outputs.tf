output "zone_id" {
  value = local.zone_id
}

output "fqdn" {
  value = aws_route53_record.alb.fqdn
}
