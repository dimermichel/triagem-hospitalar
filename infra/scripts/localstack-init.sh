#!/bin/bash
set -e

echo "==> Inicializando recursos LocalStack para desenvolvimento..."

AWS="aws --endpoint-url=http://localhost:4566 --region us-east-1"

# ── DynamoDB ──────────────────────────────────────────────────────────────────
$AWS dynamodb create-table \
  --table-name triagem-local \
  --attribute-definitions \
    AttributeName=pk,AttributeType=S \
    AttributeName=sk,AttributeType=S \
  --key-schema \
    AttributeName=pk,KeyType=HASH \
    AttributeName=sk,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  2>/dev/null || echo "Tabela triagem-local já existe"

# ── SQS Filas ─────────────────────────────────────────────────────────────────
for QUEUE in classificacao-queue painel-queue nacional-queue \
             classificacao-queue-dlq painel-queue-dlq nacional-queue-dlq; do
  $AWS sqs create-queue --queue-name "$QUEUE" 2>/dev/null || echo "Fila $QUEUE já existe"
done

echo "==> LocalStack inicializado com sucesso!"
echo "    DynamoDB: triagem-local"
echo "    SQS: classificacao-queue, painel-queue, nacional-queue"
