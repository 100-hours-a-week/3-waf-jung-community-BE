# Security Group for Interface Endpoints
resource "aws_security_group" "vpce" {
  name_prefix = "${var.project}-${var.environment}-vpce-"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = {
    Name = "${var.project}-${var.environment}-vpce-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# S3 Gateway Endpoint (FREE)
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = var.vpc_id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [var.private_route_table_id]

  tags = {
    Name = "${var.project}-${var.environment}-s3-endpoint"
  }
}

# Interface Endpoints
locals {
  interface_endpoints = {
    ecr_dkr     = "com.amazonaws.${var.region}.ecr.dkr"
    ecr_api     = "com.amazonaws.${var.region}.ecr.api"
    ssm         = "com.amazonaws.${var.region}.ssm"
    ssmmessages = "com.amazonaws.${var.region}.ssmmessages"
    ec2messages = "com.amazonaws.${var.region}.ec2messages"
    logs        = "com.amazonaws.${var.region}.logs"
  }
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_endpoints

  vpc_id              = var.vpc_id
  service_name        = each.value
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = var.app_subnet_ids
  security_group_ids  = [aws_security_group.vpce.id]

  tags = {
    Name = "${var.project}-${var.environment}-${each.key}-endpoint"
  }
}
