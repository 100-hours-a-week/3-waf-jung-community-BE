output "parameter_arns" {
  value = merge(
    { for k, v in aws_ssm_parameter.string : k => v.arn },
    { for k, v in aws_ssm_parameter.secure : k => v.arn }
  )
}

output "ssm_prefix" {
  value = "/community/${var.environment}"
}
