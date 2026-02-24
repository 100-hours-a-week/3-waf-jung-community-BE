output "be_asg_name" {
  value = aws_autoscaling_group.be.name
}

output "fe_asg_name" {
  value = aws_autoscaling_group.fe.name
}

output "be_launch_template_id" {
  value = aws_launch_template.be.id
}

output "fe_launch_template_id" {
  value = aws_launch_template.fe.id
}
