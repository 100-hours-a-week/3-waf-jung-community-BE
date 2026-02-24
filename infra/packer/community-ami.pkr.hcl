packer {
  required_plugins {
    amazon = {
      version = ">= 1.2.0"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

source "amazon-ebs" "community" {
  ami_name      = "community-loadtest-${formatdate("YYYYMMDD-hhmmss", timestamp())}"
  instance_type = "t3.micro"
  region        = var.aws_region

  source_ami_filter {
    filters = {
      name                = "al2023-ami-*-x86_64"
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    owners      = ["amazon"]
    most_recent = true
  }

  ssh_username = "ec2-user"

  launch_block_device_mappings {
    device_name           = "/dev/xvda"
    volume_size           = 16
    volume_type           = "gp3"
    delete_on_termination = true
  }

  tags = {
    Name        = "community-loadtest"
    Environment = "loadtest"
    Builder     = "packer"
  }
}

build {
  sources = ["source.amazon-ebs.community"]

  provisioner "shell" {
    script = "scripts/setup.sh"
  }
}
