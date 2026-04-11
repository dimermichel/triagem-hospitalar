#!/bin/bash

# Configuration
AWS_REGION="us-east-1"
ENV="${1:-prod}"
# Generate RDS-compliant password (exclude /, @, ", and space)
DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+@\" ')

echo "Deploying triagem-hospitalar to AWS (env=$ENV)..."

# Build all Lambda functions first
echo "Building Lambda functions..."
cd "$(dirname "$0")"
./build-all.sh
if [ $? -ne 0 ]; then
    echo "Failed to build Lambda functions"
    exit 1
fi

# Deploy infrastructure with Terraform
echo "Deploying infrastructure..."
cd ../infra/environments/$ENV
TF_DIR=$(pwd)

# Initialize Terraform
terraform init

# Apply Terraform configuration
terraform apply \
  -var="db_master_password=$DB_PASSWORD" \
  -auto-approve

if [ $? -ne 0 ]; then
    echo "Failed to deploy infrastructure"
    exit 1
fi

# Read outputs
WS_URLS=$(terraform output -json hospitais_ws_urls)
API_URLS=$(terraform output -json hospitais_api_urls)
BUCKETS=$(terraform output -json hospitais_frontend_buckets)
CF_IDS=$(terraform output -json hospitais_cloudfront_ids)
FRONTEND_URLS=$(terraform output -json hospitais_frontend_urls)
NACIONAL_QUEUE_URL=$(terraform output -raw nacional_queue_url)

echo ""
echo "Infrastructure deployed!"
echo "Nacional SQS Queue URL: $NACIONAL_QUEUE_URL"

# Build and deploy frontend for each hospital
PAINEL_DIR="$TF_DIR/../../../painel-clinico"
HOSPITALS=("einstein_sp" "hc_sp")
HOSPITAL_NOMES=("Hospital Einstein - SP" "Hospital das Clínicas - SP")

for i in "${!HOSPITALS[@]}"; do
    HOSP="${HOSPITALS[$i]}"
    NOME="${HOSPITAL_NOMES[$i]}"

    WS_URL=$(echo "$WS_URLS"  | jq -r ".$HOSP")
    API_URL=$(echo "$API_URLS" | jq -r ".$HOSP")
    BUCKET=$(echo "$BUCKETS"  | jq -r ".$HOSP")
    CF_ID=$(echo "$CF_IDS"    | jq -r ".$HOSP")
    FRONTEND_URL=$(echo "$FRONTEND_URLS" | jq -r ".$HOSP")

    echo ""
    echo "Building frontend for $NOME..."
    cd "$PAINEL_DIR"
    VITE_WS_URL="$WS_URL" \
    VITE_API_URL="$API_URL" \
    VITE_HOSPITAL_NOME="$NOME" \
    npm run build
    if [ $? -ne 0 ]; then
        echo "Failed to build frontend for $HOSP"
        exit 1
    fi

    echo "Deploying frontend for $NOME to S3..."
    aws s3 sync ./dist "s3://$BUCKET" --delete --region $AWS_REGION
    if [ $? -ne 0 ]; then
        echo "Failed to sync frontend for $HOSP to S3"
        exit 1
    fi

    echo "Invalidating CloudFront cache for $NOME..."
    aws cloudfront create-invalidation \
        --distribution-id "$CF_ID" \
        --paths '/*' \
        --region $AWS_REGION > /dev/null

    echo "✔ $NOME → $FRONTEND_URL"
done

echo ""
echo "Deployment completed successfully!"
echo ""
echo "Painel Clínico URLs:"
for i in "${!HOSPITALS[@]}"; do
    HOSP="${HOSPITALS[$i]}"
    NOME="${HOSPITAL_NOMES[$i]}"
    echo "  $NOME: $(echo "$FRONTEND_URLS" | jq -r ".$HOSP")"
done
