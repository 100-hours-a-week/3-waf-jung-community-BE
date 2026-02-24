output "alb_dns_name" {
  value = module.alb.alb_dns_name
}

output "site_url" {
  value = "https://community.${var.domain_name}"
}

output "rds_primary_endpoint" {
  value     = module.rds.primary_endpoint
  sensitive = true
}

output "rds_replica_endpoint" {
  value     = module.rds.replica_endpoint
  sensitive = true
}

output "be_asg_name" {
  value = module.asg.be_asg_name
}

output "fe_asg_name" {
  value = module.asg.fe_asg_name
}

output "ssm_prefix" {
  value = module.ssm.ssm_prefix
}
