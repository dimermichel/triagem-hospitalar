terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# ─────────────────────────────────────────────
# Variáveis do módulo
# ─────────────────────────────────────────────
variable "hospital_id" {
  description = "Identificador único do hospital (ex: hosp-einstein-sp)"
  type        = string
}

variable "env" {
  description = "Ambiente: dev | staging | prod"
  type        = string
  default     = "prod"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "nacional_queue_url" {
  description = "URL da fila SQS nacional (banco central)"
  type        = string
}

variable "nacional_queue_arn" {
  description = "ARN da fila SQS nacional (banco central)"
  type        = string
}

variable "lambda_memory_mb" {
  type    = number
  default = 512
}

variable "lambda_timeout_sec" {
  type    = number
  default = 30
}

variable "hospital_nome" {
  description = "Nome do hospital exibido no painel clínico"
  type        = string
}

variable "enable_frontend" {
  description = "Habilita S3 + CloudFront para o painel clínico"
  type        = bool
  default     = true
}

locals {
  prefix = "${var.env}-${var.hospital_id}"
  tags = {
    Hospital    = var.hospital_id
    Environment = var.env
    ManagedBy   = "terraform"
    System      = "triagem-hospitalar"
  }
}

# ─────────────────────────────────────────────
# DynamoDB — tabela local do hospital
# ─────────────────────────────────────────────
resource "aws_dynamodb_table" "triagem" {
  name         = "${local.prefix}-triagem"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  range_key    = "sk"

  attribute {
    name = "pk"
    type = "S"
  }
  attribute {
    name = "sk"
    type = "S"
  }

  ttl {
    attribute_name = "ttl_expira_em"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = var.env == "prod"
  }

  tags = local.tags
}

# ─────────────────────────────────────────────
# SQS — filas com DLQ
# ─────────────────────────────────────────────

# DLQ para classificação
resource "aws_sqs_queue" "classificacao_dlq" {
  name                      = "${local.prefix}-classificacao-dlq"
  message_retention_seconds = 1209600  # 14 dias
  tags                      = local.tags
}

# Fila de classificação (ms-triagem → ms-manchester)
resource "aws_sqs_queue" "classificacao" {
  name                       = "${local.prefix}-classificacao"
  visibility_timeout_seconds = 60
  message_retention_seconds  = 86400  # 1 dia

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.classificacao_dlq.arn
    maxReceiveCount     = 3
  })

  tags = local.tags
}

# DLQ para painel
resource "aws_sqs_queue" "painel_dlq" {
  name                      = "${local.prefix}-painel-dlq"
  message_retention_seconds = 86400
  tags                      = local.tags
}

# Fila do painel WebSocket
resource "aws_sqs_queue" "painel" {
  name                       = "${local.prefix}-painel"
  visibility_timeout_seconds = 30

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.painel_dlq.arn
    maxReceiveCount     = 3
  })

  tags = local.tags
}

# ─────────────────────────────────────────────
# IAM Role — Lambda execution
# ─────────────────────────────────────────────
resource "aws_iam_role" "lambda_exec" {
  name = "${local.prefix}-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })

  tags = local.tags
}

resource "aws_iam_role_policy" "lambda_policy" {
  name = "${local.prefix}-lambda-policy"
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect = "Allow"
        Action = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem",
                  "dynamodb:DeleteItem", "dynamodb:Query", "dynamodb:Scan"]
        Resource = aws_dynamodb_table.triagem.arn
      },
      {
        Effect   = "Allow"
        Action   = ["sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage",
                    "sqs:GetQueueAttributes"]
        Resource = [
          aws_sqs_queue.classificacao.arn,
          aws_sqs_queue.painel.arn,
          var.nacional_queue_arn
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# ─────────────────────────────────────────────
# Lambda — ms-triagem
# ─────────────────────────────────────────────
resource "aws_lambda_function" "ms_triagem" {
  function_name = "${local.prefix}-ms-triagem"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  runtime       = "java21"
  memory_size   = var.lambda_memory_mb
  timeout       = var.lambda_timeout_sec

  filename         = "${path.module}/../../../ms-triagem/target/function.zip"
  source_code_hash = filebase64sha256("${path.module}/../../../ms-triagem/target/function.zip")

  environment {
    variables = {
      HOSPITAL_ID            = var.hospital_id
      DYNAMODB_TABLE_TRIAGEM = aws_dynamodb_table.triagem.name
      SQS_CLASSIFICACAO_URL  = aws_sqs_queue.classificacao.url
      AWS_REGION_OVERRIDE    = var.aws_region
    }
  }

  snap_start {
    apply_on = "PublishedVersions"
  }

  tags = local.tags
}

# ─────────────────────────────────────────────
# Lambda — ms-manchester
# ─────────────────────────────────────────────
resource "aws_lambda_function" "ms_manchester" {
  function_name = "${local.prefix}-ms-manchester"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  runtime       = "java21"
  memory_size   = var.lambda_memory_mb
  timeout       = var.lambda_timeout_sec

  filename         = "${path.module}/../../../ms-manchester/target/function.zip"
  source_code_hash = filebase64sha256("${path.module}/../../../ms-manchester/target/function.zip")

  environment {
    variables = {
      QUARKUS_LAMBDA_HANDLER = "classificacao"
      HOSPITAL_ID            = var.hospital_id
      DYNAMODB_TABLE_TRIAGEM = aws_dynamodb_table.triagem.name
      SQS_PAINEL_URL         = aws_sqs_queue.painel.url
    }
  }

  tags = local.tags
}

# Trigger SQS → ms-manchester
resource "aws_lambda_event_source_mapping" "classificacao_trigger" {
  event_source_arn = aws_sqs_queue.classificacao.arn
  function_name    = aws_lambda_function.ms_manchester.arn
  batch_size       = 5
  enabled          = true
}

# ─────────────────────────────────────────────
# Lambda — ms-consolidador
# ─────────────────────────────────────────────
resource "aws_lambda_function" "ms_consolidador" {
  function_name = "${local.prefix}-ms-consolidador"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  runtime       = "java21"
  memory_size   = 1024
  timeout       = 300  # 5 min para consolidar

  filename         = "${path.module}/../../../ms-consolidador/target/function.zip"
  source_code_hash = filebase64sha256("${path.module}/../../../ms-consolidador/target/function.zip")

  environment {
    variables = {
      QUARKUS_LAMBDA_HANDLER = "consolidador"
      HOSPITAL_ID            = var.hospital_id
      DYNAMODB_TABLE_TRIAGEM = aws_dynamodb_table.triagem.name
      SQS_NACIONAL_URL       = var.nacional_queue_url
    }
  }

  tags = local.tags
}

# EventBridge — dispara consolidador às 23h55 (Brasília = 02h55 UTC)
resource "aws_cloudwatch_event_rule" "consolidador_schedule" {
  name                = "${local.prefix}-consolidador-schedule"
  description         = "Consolidação diária ${var.hospital_id}"
  schedule_expression = "cron(55 2 * * ? *)"
  tags                = local.tags
}

resource "aws_cloudwatch_event_target" "consolidador_target" {
  rule      = aws_cloudwatch_event_rule.consolidador_schedule.name
  target_id = "ConsolidadorLambda"
  arn       = aws_lambda_function.ms_consolidador.arn
}

resource "aws_lambda_permission" "allow_eventbridge_consolidador" {
  statement_id  = "AllowEventBridgeInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.ms_consolidador.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.consolidador_schedule.arn
}

# ─────────────────────────────────────────────
# API Gateway HTTP para ms-triagem
# ─────────────────────────────────────────────
resource "aws_apigatewayv2_api" "triagem_api" {
  name          = "${local.prefix}-api"
  protocol_type = "HTTP"
  tags          = local.tags

  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["GET", "POST", "DELETE", "OPTIONS"]
    allow_headers = ["Content-Type", "Authorization"]
  }
}

resource "aws_apigatewayv2_integration" "triagem_integration" {
  api_id                 = aws_apigatewayv2_api.triagem_api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.ms_triagem.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "triagem_route" {
  api_id    = aws_apigatewayv2_api.triagem_api.id
  route_key = "$default"
  target    = "integrations/${aws_apigatewayv2_integration.triagem_integration.id}"
}

resource "aws_apigatewayv2_stage" "triagem_stage" {
  api_id      = aws_apigatewayv2_api.triagem_api.id
  name        = "$default"
  auto_deploy = true
  tags        = local.tags
}

resource "aws_lambda_permission" "allow_apigw" {
  statement_id  = "AllowAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.ms_triagem.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.triagem_api.execution_arn}/*/*"
}

# ─────────────────────────────────────────────
# CloudWatch — alarmes
# ─────────────────────────────────────────────
resource "aws_cloudwatch_metric_alarm" "classificacao_dlq_alarm" {
  alarm_name          = "${local.prefix}-classificacao-dlq-nao-vazia"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Mensagens na DLQ de classificação — investigar falhas de triagem"

  dimensions = {
    QueueName = aws_sqs_queue.classificacao_dlq.name
  }

  tags = local.tags
}
