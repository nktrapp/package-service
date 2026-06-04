resource "aws_ecr_repository" "package_service" {
  name                 = "${var.project_name}/package-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Service = "package-service"
  }
}

resource "aws_ecr_lifecycle_policy" "package_service" {
  repository = aws_ecr_repository.package_service.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 2 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 2
      }
      action = {
        type = "expire"
      }
    }]
  })
}
