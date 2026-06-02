resource "aws_secretsmanager_secret" "mongodb" {
  name = "${var.project_name}/${var.environment}/package-service/mongodb"

  tags = {
    Name = "${var.project_name}-package-mongodb-secret"
  }
}

resource "aws_secretsmanager_secret_version" "mongodb" {
  secret_id = aws_secretsmanager_secret.mongodb.id
  secret_string = jsonencode({
    username    = var.docdb_master_username
    password    = var.docdb_master_password
    endpoint    = aws_docdb_cluster.main.endpoint
    uri         = "mongodb://${var.docdb_master_username}:${var.docdb_master_password}@${aws_docdb_cluster.main.endpoint}:27017/?tls=true&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false&authSource=admin"
    package_uri = "mongodb://${var.docdb_master_username}:${var.docdb_master_password}@${aws_docdb_cluster.main.endpoint}:27017/package_db?tls=true&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false&authSource=admin"
  })
}
