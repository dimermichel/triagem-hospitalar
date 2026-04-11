output "api_url" {
  description = "URL base da API do hospital"
  value       = aws_apigatewayv2_stage.triagem_stage.invoke_url
}

output "classificacao_queue_url" {
  value = aws_sqs_queue.classificacao.url
}

output "painel_queue_url" {
  value = aws_sqs_queue.painel.url
}

output "dynamodb_table_name" {
  value = aws_dynamodb_table.triagem.name
}

output "ms_triagem_function_name" {
  value = aws_lambda_function.ms_triagem.function_name
}

output "ms_manchester_function_name" {
  value = aws_lambda_function.ms_manchester.function_name
}

output "ms_consolidador_function_name" {
  value = aws_lambda_function.ms_consolidador.function_name
}

output "ws_url" {
  description = "WebSocket URL do painel (wss://)"
  value       = aws_apigatewayv2_stage.ws_stage.invoke_url
}

output "frontend_url" {
  description = "URL pública do painel clínico"
  value       = var.enable_frontend ? "https://${aws_cloudfront_distribution.frontend[0].domain_name}" : null
}

output "frontend_bucket_name" {
  value = var.enable_frontend ? aws_s3_bucket.frontend[0].bucket : null
}

output "cloudfront_distribution_id" {
  value = var.enable_frontend ? aws_cloudfront_distribution.frontend[0].id : null
}
