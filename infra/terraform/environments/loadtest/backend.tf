# Step 1: Start with local backend
# Step 2: After S3 bucket is created, uncomment below and run: terraform init -migrate-state

# terraform {
#   backend "s3" {
#     bucket = "community-loadtest-tf-state"
#     key    = "loadtest/terraform.tfstate"
#     region = "ap-northeast-2"
#   }
# }
