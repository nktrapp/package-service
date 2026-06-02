# ─── Package Events Queue (FIFO) ───
# Owned by the package-service stack. package-service PUBLISHES here; the
# logistics-service stack CONSUMES it via a data lookup.
resource "aws_sqs_queue" "package_events_dlq" {
  name                        = "${var.project_name}-package-events-dlq.fifo"
  fifo_queue                  = true
  content_based_deduplication = false
  message_retention_seconds   = 1209600 # 14 days

  tags = {
    Service = "package-service"
    Type    = "dlq"
  }
}

resource "aws_sqs_queue" "package_events" {
  name                        = "${var.project_name}-package-events-queue.fifo"
  fifo_queue                  = true
  content_based_deduplication = false
  visibility_timeout_seconds  = 60
  message_retention_seconds   = 345600 # 4 days
  receive_wait_time_seconds   = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.package_events_dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Service = "package-service"
    Type    = "main"
  }
}
