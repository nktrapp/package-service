# ─── SNS Topic for Alarms ───
resource "aws_sns_topic" "alarms" {
  name = "${var.project_name}-package-service-alarms"
}

# ─── CPU Alarm ───
resource "aws_cloudwatch_metric_alarm" "package_cpu_high" {
  alarm_name          = "${var.project_name}-package-service-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Package Service CPU > 80%"
  alarm_actions       = [aws_sns_topic.alarms.arn]

  dimensions = {
    ClusterName = local.ecs_cluster_name
    ServiceName = aws_ecs_service.package_service.name
  }
}

# ─── Memory Alarm ───
resource "aws_cloudwatch_metric_alarm" "package_memory_high" {
  alarm_name          = "${var.project_name}-package-service-memory-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = 300
  statistic           = "Average"
  threshold           = 85
  alarm_description   = "Package Service Memory > 85%"
  alarm_actions       = [aws_sns_topic.alarms.arn]

  dimensions = {
    ClusterName = local.ecs_cluster_name
    ServiceName = aws_ecs_service.package_service.name
  }
}

# ─── DLQ Alarm ───
resource "aws_cloudwatch_metric_alarm" "package_dlq_not_empty" {
  alarm_name          = "${var.project_name}-package-events-dlq-not-empty"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Package Events DLQ has messages - failed message processing"
  alarm_actions       = [aws_sns_topic.alarms.arn]

  dimensions = {
    QueueName = aws_sqs_queue.package_events_dlq.name
  }
}
