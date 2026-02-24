output "alb_arn" {
  value = aws_lb.main.arn
}

output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "alb_zone_id" {
  value = aws_lb.main.zone_id
}

output "alb_arn_suffix" {
  value = aws_lb.main.arn_suffix
}

output "be_target_group_arn" {
  value = aws_lb_target_group.be.arn
}

output "fe_target_group_arn" {
  value = aws_lb_target_group.fe.arn
}

output "be_tg_arn_suffix" {
  value = aws_lb_target_group.be.arn_suffix
}

output "https_listener_arn" {
  value = aws_lb_listener.https.arn
}

output "acm_certificate_arn" {
  value = aws_acm_certificate.main.arn
}
