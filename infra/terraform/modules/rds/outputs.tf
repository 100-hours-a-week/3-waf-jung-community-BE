output "primary_endpoint" {
  value = aws_db_instance.primary.endpoint
}

output "primary_address" {
  value = aws_db_instance.primary.address
}

output "replica_endpoint" {
  value = aws_db_instance.replica.endpoint
}

output "replica_address" {
  value = aws_db_instance.replica.address
}

output "db_name" {
  value = aws_db_instance.primary.db_name
}
