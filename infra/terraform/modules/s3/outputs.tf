output "image_bucket_name" {
  value = aws_s3_bucket.images.bucket
}

output "image_bucket_arn" {
  value = aws_s3_bucket.images.arn
}

output "alb_logs_bucket_name" {
  value = aws_s3_bucket.alb_logs.bucket
}

output "alb_logs_bucket_arn" {
  value = aws_s3_bucket.alb_logs.arn
}
