resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-${var.environment}-db-subnet"
  subnet_ids = var.data_subnet_ids

  tags = {
    Name        = "${var.project}-${var.environment}-db-subnet"
    Environment = var.environment
  }
}

resource "aws_db_parameter_group" "main" {
  name   = "${var.project}-${var.environment}-mysql80"
  family = "mysql8.0"

  parameter {
    name  = "max_connections"
    value = "300"
  }

  parameter {
    name  = "innodb_flush_log_at_trx_commit"
    value = "2"
  }

  parameter {
    name  = "innodb_log_buffer_size"
    value = "67108864"
  }

  parameter {
    name  = "slow_query_log"
    value = "1"
  }

  parameter {
    name  = "long_query_time"
    value = "1"
  }

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_unicode_ci"
  }

  tags = {
    Name = "${var.project}-${var.environment}-mysql80"
  }
}

resource "aws_db_instance" "primary" {
  identifier     = "${var.project}-${var.environment}-primary"
  engine         = "mysql"
  engine_version = "8.0"

  instance_class        = var.primary_instance_class
  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.rds_sg_id]
  parameter_group_name   = aws_db_parameter_group.main.name

  multi_az            = false
  publicly_accessible = false

  backup_retention_period = 1
  skip_final_snapshot     = true
  deletion_protection     = false

  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  tags = {
    Name        = "${var.project}-${var.environment}-primary"
    Environment = var.environment
  }
}

resource "aws_db_instance" "replica" {
  identifier          = "${var.project}-${var.environment}-replica"
  replicate_source_db = aws_db_instance.primary.identifier

  instance_class = var.replica_instance_class
  storage_type   = "gp3"

  vpc_security_group_ids = [var.rds_sg_id]
  parameter_group_name   = aws_db_parameter_group.main.name

  multi_az            = false
  publicly_accessible = false

  skip_final_snapshot = true
  deletion_protection = false

  # db.t3.small does not support Performance Insights
  performance_insights_enabled = false

  tags = {
    Name        = "${var.project}-${var.environment}-replica"
    Environment = var.environment
  }
}
