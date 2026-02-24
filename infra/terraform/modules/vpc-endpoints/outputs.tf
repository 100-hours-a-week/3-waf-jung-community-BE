output "s3_endpoint_id" {
  value = aws_vpc_endpoint.s3.id
}

output "vpce_sg_id" {
  value = aws_security_group.vpce.id
}
