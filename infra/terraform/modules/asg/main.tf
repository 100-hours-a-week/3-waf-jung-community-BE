# BE Launch Template
resource "aws_launch_template" "be" {
  name_prefix   = "${var.project}-${var.environment}-be-"
  image_id      = var.ami_id
  instance_type = "t3.medium"

  iam_instance_profile {
    name = var.instance_profile_name
  }

  vpc_security_group_ids = [var.be_sg_id]

  user_data = base64encode(templatefile("${path.module}/templates/be-user-data.sh.tpl", {
    region       = var.region
    ssm_prefix   = var.ssm_prefix
    ecr_registry = var.ecr_registry
    image_name   = var.image_name
  }))

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name        = "${var.project}-${var.environment}-be"
      Environment = var.environment
      Role        = "backend"
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}

# FE Launch Template
resource "aws_launch_template" "fe" {
  name_prefix   = "${var.project}-${var.environment}-fe-"
  image_id      = var.ami_id
  instance_type = "t3.micro"

  iam_instance_profile {
    name = var.instance_profile_name
  }

  vpc_security_group_ids = [var.fe_sg_id]

  user_data = base64encode(templatefile("${path.module}/templates/fe-user-data.sh.tpl", {
    region       = var.region
    ssm_prefix   = var.ssm_prefix
    ecr_registry = var.ecr_registry
    image_name   = var.image_name
  }))

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name        = "${var.project}-${var.environment}-fe"
      Environment = var.environment
      Role        = "frontend"
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}

# BE ASG
resource "aws_autoscaling_group" "be" {
  name                      = "${var.project}-${var.environment}-be-asg"
  vpc_zone_identifier       = var.app_subnet_ids
  target_group_arns         = [var.be_target_group_arn]
  health_check_type         = "ELB"
  health_check_grace_period = 180

  min_size         = 2
  max_size         = 4
  desired_capacity = 2

  mixed_instances_policy {
    instances_distribution {
      on_demand_base_capacity                  = 0
      on_demand_percentage_above_base_capacity = 0
      spot_allocation_strategy                 = "capacity-optimized"
    }

    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.be.id
        version            = "$Latest"
      }

      override {
        instance_type = "t3.medium"
      }
      override {
        instance_type = "t3a.medium"
      }
      override {
        instance_type = "t2.medium"
      }
    }
  }

  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 50
      max_healthy_percentage = 150
      instance_warmup        = 180
    }
  }

  tag {
    key                 = "Name"
    value               = "${var.project}-${var.environment}-be"
    propagate_at_launch = true
  }

  tag {
    key                 = "Environment"
    value               = var.environment
    propagate_at_launch = true
  }
}

# FE ASG
resource "aws_autoscaling_group" "fe" {
  name                      = "${var.project}-${var.environment}-fe-asg"
  vpc_zone_identifier       = var.app_subnet_ids
  target_group_arns         = [var.fe_target_group_arn]
  health_check_type         = "ELB"
  health_check_grace_period = 60

  min_size         = 2
  max_size         = 2
  desired_capacity = 2

  mixed_instances_policy {
    instances_distribution {
      on_demand_base_capacity                  = 0
      on_demand_percentage_above_base_capacity = 0
      spot_allocation_strategy                 = "capacity-optimized"
    }

    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.fe.id
        version            = "$Latest"
      }

      override {
        instance_type = "t3.micro"
      }
      override {
        instance_type = "t3a.micro"
      }
    }
  }

  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 50
      max_healthy_percentage = 150
      instance_warmup        = 60
    }
  }

  tag {
    key                 = "Name"
    value               = "${var.project}-${var.environment}-fe"
    propagate_at_launch = true
  }

  tag {
    key                 = "Environment"
    value               = var.environment
    propagate_at_launch = true
  }
}

# BE Auto Scaling Policies
resource "aws_autoscaling_policy" "be_cpu" {
  name                   = "${var.project}-${var.environment}-be-cpu"
  autoscaling_group_name = aws_autoscaling_group.be.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ASGAverageCPUUtilization"
    }
    target_value = 60.0
  }
}

resource "aws_autoscaling_policy" "be_alb_requests" {
  name                   = "${var.project}-${var.environment}-be-alb-req"
  autoscaling_group_name = aws_autoscaling_group.be.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${var.alb_arn_suffix}/${var.be_target_group_arn_suffix}"
    }
    target_value = 200.0
  }
}
