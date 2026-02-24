output "alb_sg_id" {
  value = aws_security_group.alb.id
}

output "be_sg_id" {
  value = aws_security_group.be.id
}

output "fe_sg_id" {
  value = aws_security_group.fe.id
}

output "rds_sg_id" {
  value = aws_security_group.rds.id
}
