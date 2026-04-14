# ── ms-nacional-consumer: Lambda + SQS trigger + VPC + IAM ───────────────────
# Lambda global (não por hospital) que consome a fila SQS nacional e persiste
# os consolidados diários no Aurora PostgreSQL (sus-triagem-nacional).

locals {
  nacional_tags = {
    ManagedBy = "terraform"
    System    = "triagem-hospitalar"
    Env       = "prod"
    Service   = "nacional-consumer"
  }
}

# ─────────────────────────────────────────────
# VPC — default VPC e subnets para conectividade Lambda ↔ Aurora
# ─────────────────────────────────────────────

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# ─────────────────────────────────────────────
# Security Groups
# ─────────────────────────────────────────────

# SG do Lambda — permite saida irrestrita (para SQS, CloudWatch, Aurora)
resource "aws_security_group" "nacional_lambda" {
  name        = "prod-nacional-consumer-lambda-sg"
  description = "Lambda ms-nacional-consumer - saida irrestrita"
  vpc_id      = data.aws_vpc.default.id

  egress {
    description = "Saida irrestrita"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.nacional_tags
}

# SG do Aurora — permite entrada do Lambda na porta 5432
resource "aws_security_group" "nacional_rds" {
  name        = "prod-nacional-rds-sg"
  description = "Aurora Nacional - permite acesso PostgreSQL do Lambda"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "PostgreSQL do Lambda nacional-consumer"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.nacional_lambda.id]
  }

  egress {
    description = "Saida irrestrita"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.nacional_tags
}

# ─────────────────────────────────────────────
# IAM — Role para o Lambda nacional-consumer
# ─────────────────────────────────────────────

resource "aws_iam_role" "nacional_consumer" {
  name = "prod-nacional-consumer-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })

  tags = local.nacional_tags
}

resource "aws_iam_role_policy" "nacional_consumer" {
  name = "prod-nacional-consumer-policy"
  role = aws_iam_role.nacional_consumer.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "SQSNacional"
        Effect   = "Allow"
        Action   = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:ChangeMessageVisibility"
        ]
        Resource = aws_sqs_queue.nacional.arn
      }
    ]
  })
}

# AWSLambdaVPCAccessExecutionRole inclui logs + permissões de ENI (VPC)
resource "aws_iam_role_policy_attachment" "nacional_consumer_vpc_exec" {
  role       = aws_iam_role.nacional_consumer.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

# ─────────────────────────────────────────────
# Lambda — ms-nacional-consumer
# ─────────────────────────────────────────────

resource "aws_lambda_function" "ms_nacional_consumer" {
  function_name = "prod-ms-nacional-consumer"
  role          = aws_iam_role.nacional_consumer.arn
  handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  runtime       = "java21"
  memory_size   = 512
  timeout       = 300  # 5 min — lotes diários podem ser grandes

  filename         = "${path.module}/../../../ms-nacional-consumer/target/function.zip"
  source_code_hash = filebase64sha256("${path.module}/../../../ms-nacional-consumer/target/function.zip")

  environment {
    variables = {
      QUARKUS_LAMBDA_HANDLER = "nacional-consumer"
      DB_URL                 = "jdbc:postgresql://${aws_rds_cluster.nacional.endpoint}:5432/triagem_nacional"
      DB_USERNAME            = aws_rds_cluster.nacional.master_username
      DB_PASSWORD            = var.db_master_password
    }
  }

  vpc_config {
    subnet_ids         = data.aws_subnets.default.ids
    security_group_ids = [aws_security_group.nacional_lambda.id]
  }

  tags = local.nacional_tags
}

# ─────────────────────────────────────────────
# SQS → Lambda trigger
# ─────────────────────────────────────────────

resource "aws_lambda_event_source_mapping" "nacional_trigger" {
  event_source_arn = aws_sqs_queue.nacional.arn
  function_name    = aws_lambda_function.ms_nacional_consumer.arn
  batch_size       = 10

  # Acumula mensagens por até 30s antes de acionar o Lambda (melhora throughput)
  maximum_batching_window_in_seconds = 30

  enabled = true
}

# ─────────────────────────────────────────────
# CloudWatch — alarme na DLQ nacional
# ─────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "nacional_dlq_alarm" {
  alarm_name          = "prod-nacional-dlq-nao-vazia"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Mensagens na DLQ nacional — falha na importação para Aurora"

  dimensions = {
    QueueName = aws_sqs_queue.nacional_dlq.name
  }

  tags = local.nacional_tags
}
