data "aws_caller_identity" "current" {}

# ─── Networking ───
module "vpc" {
  source      = "../../modules/vpc"
  project     = var.project
  environment = var.environment
}

module "vpc_endpoints" {
  source                 = "../../modules/vpc-endpoints"
  project                = var.project
  environment            = var.environment
  region                 = var.region
  vpc_id                 = module.vpc.vpc_id
  vpc_cidr               = module.vpc.vpc_cidr
  app_subnet_ids         = module.vpc.app_subnet_ids
  private_route_table_id = module.vpc.private_route_table_id
}

module "security_groups" {
  source      = "../../modules/security-groups"
  project     = var.project
  environment = var.environment
  vpc_id      = module.vpc.vpc_id
}

# ─── IAM ───
module "iam" {
  source         = "../../modules/iam"
  project        = var.project
  environment    = var.environment
  region         = var.region
  account_id     = var.account_id
  s3_bucket_name = var.image_bucket_name
}

# ─── Storage ───
module "s3" {
  source            = "../../modules/s3"
  project           = var.project
  environment       = var.environment
  image_bucket_name = var.image_bucket_name
  domain_name       = var.domain_name
  seed_sql_path     = "${path.module}/../../../../scripts/03_insert_dummy_small.sql"
}

# ─── Database ───
module "rds" {
  source          = "../../modules/rds"
  project         = var.project
  environment     = var.environment
  data_subnet_ids = module.vpc.data_subnet_ids
  rds_sg_id       = module.security_groups.rds_sg_id
  db_username     = var.db_username
  db_password     = var.db_password
}

# ─── DNS (create zone or use existing) ───
module "route53" {
  source           = "../../modules/route53"
  environment      = var.environment
  domain_name      = var.domain_name
  create_zone      = var.existing_route53_zone_id == "" ? true : false
  existing_zone_id = var.existing_route53_zone_id
  alb_dns_name     = module.alb.alb_dns_name
  alb_zone_id      = module.alb.alb_zone_id
}

# ─── Load Balancer ───
module "alb" {
  source               = "../../modules/alb"
  project              = var.project
  environment          = var.environment
  vpc_id               = module.vpc.vpc_id
  public_subnet_ids    = module.vpc.public_subnet_ids
  alb_sg_id            = module.security_groups.alb_sg_id
  domain_name          = var.domain_name
  route53_zone_id      = var.existing_route53_zone_id != "" ? var.existing_route53_zone_id : module.route53.zone_id
  alb_logs_bucket_name = module.s3.alb_logs_bucket_name
}

# ─── SSM Parameters ───
module "ssm" {
  source          = "../../modules/ssm"
  environment     = var.environment
  region          = var.region
  s3_bucket_name  = module.s3.image_bucket_name
  frontend_url    = "https://community.${var.domain_name}"
  db_url          = "jdbc:mysql://${module.rds.primary_address}:3306/community?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull"
  db_username     = var.db_username
  db_password     = var.db_password
  db_readonly_url = "jdbc:mysql://${module.rds.replica_address}:3306/community?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull"
  jwt_secret      = var.jwt_secret
}

# ─── Auto Scaling Groups ───
module "asg" {
  source                     = "../../modules/asg"
  project                    = var.project
  environment                = var.environment
  region                     = var.region
  ami_id                     = var.ami_id
  instance_profile_name      = module.iam.ec2_instance_profile_name
  app_subnet_ids             = module.vpc.app_subnet_ids
  be_sg_id                   = module.security_groups.be_sg_id
  fe_sg_id                   = module.security_groups.fe_sg_id
  be_target_group_arn        = module.alb.be_target_group_arn
  fe_target_group_arn        = module.alb.fe_target_group_arn
  ssm_prefix                 = module.ssm.ssm_prefix
  ecr_registry               = var.ecr_registry
  alb_arn_suffix             = module.alb.alb_arn_suffix
  be_target_group_arn_suffix = module.alb.be_tg_arn_suffix
}

# ─── Billing Alarm ───
resource "aws_cloudwatch_metric_alarm" "billing" {
  alarm_name          = "${var.project}-${var.environment}-billing-alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "EstimatedCharges"
  namespace           = "AWS/Billing"
  period              = 21600
  statistic           = "Maximum"
  threshold           = 20
  alarm_description   = "Billing alarm: estimated charges exceed $20"

  dimensions = {
    Currency = "USD"
  }

  tags = {
    Environment = var.environment
  }
}
