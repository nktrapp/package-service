output "package_service_url" {
  description = "Package Service URL via the shared ALB"
  value       = "http://${data.terraform_remote_state.base.outputs.alb_dns_name}/api/v1/packages"
}

output "ecr_repository_url" {
  description = "Package Service ECR repository URL"
  value       = aws_ecr_repository.package_service.repository_url
}

output "package_events_queue_url" {
  description = "package-events-queue.fifo URL"
  value       = local.package_events_queue_url
}

output "package_events_queue_arn" {
  description = "package-events-queue.fifo ARN"
  value       = local.package_events_queue_arn
}

output "documentdb_endpoint" {
  description = "DocumentDB cluster endpoint"
  value       = aws_docdb_cluster.main.endpoint
}

output "mongodb_secret_arn" {
  description = "MongoDB secret ARN"
  value       = aws_secretsmanager_secret.mongodb.arn
}
