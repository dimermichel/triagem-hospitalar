# ─────────────────────────────────────────────────────────────────────────────
# Adição ao módulo hospital: WebSocket API Gateway + ms-painel-ws Lambdas
# Este arquivo complementa infra/modules/hospital/main.tf
# ─────────────────────────────────────────────────────────────────────────────

# DynamoDB — tabela de conexões WebSocket ativas
resource "aws_dynamodb_table" "conexoes_ws" {
  name         = "${local.prefix}-conexoes-ws"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "connectionId"

  attribute {
    name = "connectionId"
    type = "S"
  }

  attribute {
    name = "hospitalId"
    type = "S"
  }

  global_secondary_index {
    name            = "hospital-index"
    hash_key        = "hospitalId"
    projection_type = "ALL"
  }

  ttl {
    attribute_name = "ttlExpiraEm"
    enabled        = true
  }

  tags = local.tags
}

# Lambda — WebSocket handler ($connect / $disconnect / $default)
resource "aws_lambda_function" "ms_painel_ws_handler" {
  function_name = "${local.prefix}-ms-painel-ws-handler"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  runtime       = "java21"
  memory_size   = var.lambda_memory_mb
  timeout       = 29  # API GW WebSocket max timeout

  filename         = "${path.module}/../../../ms-painel-ws/target/function.zip"
  source_code_hash = filebase64sha256("${path.module}/../../../ms-painel-ws/target/function.zip")

  environment {
    variables = {
      QUARKUS_LAMBDA_HANDLER   = "ws-handler"
      HOSPITAL_ID              = var.hospital_id
      DYNAMODB_TABLE_CONEXOES  = aws_dynamodb_table.conexoes_ws.name
      DYNAMODB_TABLE_TRIAGEM   = aws_dynamodb_table.triagem.name
      WS_ENDPOINT_URL          = "https://${aws_apigatewayv2_api.painel_ws.id}.execute-api.${var.aws_region}.amazonaws.com/${var.env}"
    }
  }

  tags = local.tags
}

# Lambda — SQS fan-out (painel queue → WebSocket clientes)
resource "aws_lambda_function" "ms_painel_ws_fanout" {
  function_name = "${local.prefix}-ms-painel-ws-fanout"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  runtime       = "java21"
  memory_size   = var.lambda_memory_mb
  timeout       = 30

  filename         = "${path.module}/../../../ms-painel-ws/target/function.zip"
  source_code_hash = filebase64sha256("${path.module}/../../../ms-painel-ws/target/function.zip")

  environment {
    variables = {
      QUARKUS_LAMBDA_HANDLER   = "sqs-fanout"
      HOSPITAL_ID              = var.hospital_id
      DYNAMODB_TABLE_CONEXOES  = aws_dynamodb_table.conexoes_ws.name
      DYNAMODB_TABLE_TRIAGEM   = aws_dynamodb_table.triagem.name
      WS_ENDPOINT_URL          = "https://${aws_apigatewayv2_api.painel_ws.id}.execute-api.${var.aws_region}.amazonaws.com/${var.env}"
    }
  }

  tags = local.tags
}

# SQS → fanout Lambda trigger
resource "aws_lambda_event_source_mapping" "painel_fanout_trigger" {
  event_source_arn = aws_sqs_queue.painel.arn
  function_name    = aws_lambda_function.ms_painel_ws_fanout.arn
  batch_size       = 10
  enabled          = true
}

# ── API Gateway WebSocket ──────────────────────────────────────────────────

resource "aws_apigatewayv2_api" "painel_ws" {
  name                       = "${local.prefix}-painel-ws"
  protocol_type              = "WEBSOCKET"
  route_selection_expression = "$request.body.tipo"
  tags                       = local.tags
}

resource "aws_apigatewayv2_integration" "ws_integration" {
  api_id           = aws_apigatewayv2_api.painel_ws.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.ms_painel_ws_handler.invoke_arn
}

resource "aws_apigatewayv2_route" "ws_connect" {
  api_id    = aws_apigatewayv2_api.painel_ws.id
  route_key = "$connect"
  target    = "integrations/${aws_apigatewayv2_integration.ws_integration.id}"
}

resource "aws_apigatewayv2_route" "ws_disconnect" {
  api_id    = aws_apigatewayv2_api.painel_ws.id
  route_key = "$disconnect"
  target    = "integrations/${aws_apigatewayv2_integration.ws_integration.id}"
}

resource "aws_apigatewayv2_route" "ws_default" {
  api_id    = aws_apigatewayv2_api.painel_ws.id
  route_key = "$default"
  target    = "integrations/${aws_apigatewayv2_integration.ws_integration.id}"
}

resource "aws_apigatewayv2_stage" "ws_stage" {
  api_id      = aws_apigatewayv2_api.painel_ws.id
  name        = var.env
  auto_deploy = true
  tags        = local.tags
}

resource "aws_lambda_permission" "allow_ws_apigw" {
  statement_id  = "AllowWSAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.ms_painel_ws_handler.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.painel_ws.execution_arn}/*/*"
}

# IAM: Lambda precisa de permissão para gerenciar conexões WebSocket
resource "aws_iam_role_policy" "ws_management_policy" {
  name = "${local.prefix}-ws-management"
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["execute-api:ManageConnections"]
        Resource = "${aws_apigatewayv2_api.painel_ws.execution_arn}/*"
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:DeleteItem",
                    "dynamodb:Query"]
        Resource = [
          aws_dynamodb_table.conexoes_ws.arn,
          "${aws_dynamodb_table.conexoes_ws.arn}/index/*"
        ]
      }
    ]
  })
}
