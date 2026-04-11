#!/bin/bash

# Configuration
AWS_REGION="us-east-1"
ENV="${1:-prod}"

echo "Destroying triagem-hospitalar infrastructure (env=$ENV)..."
echo "WARNING: This will delete all resources including database data!"
read -p "Are you sure you want to continue? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Destroy cancelled."
    exit 0
fi

# Navigate to Terraform directory
echo "Destroying infrastructure with Terraform..."
cd "$(dirname "$0")/../infra/environments/$ENV"

# Password is not needed for destruction but is a required variable
DB_PASSWORD="Placeholder1!"

# Disable RDS deletion protection so terraform can delete the cluster
echo "Disabling RDS deletion protection..."
terraform apply \
  -var="db_master_password=$DB_PASSWORD" \
  -var="deletion_protection=false" \
  -target=aws_rds_cluster.nacional \
  -auto-approve

if [ $? -ne 0 ]; then
    echo "Failed to disable RDS deletion protection"
    exit 1
fi

# Empty frontend S3 buckets (force_destroy is false in prod)
echo "Emptying frontend S3 buckets..."
for bucket in $(aws s3api list-buckets --query "Buckets[?starts_with(Name, 'sus-triagem-prod-') && ends_with(Name, '-frontend')].Name" --output text 2>/dev/null); do
    echo "  Emptying $bucket..."
    aws s3 rm "s3://$bucket" --recursive --region $AWS_REGION
done

# Destroy Terraform resources
terraform destroy \
  -var="db_master_password=$DB_PASSWORD" \
  -var="deletion_protection=false" \
  -auto-approve

if [ $? -ne 0 ]; then
    echo "Failed to destroy infrastructure"
    echo "You may need to manually delete some resources or run:"
    echo "  cd infra/environments/$ENV && terraform destroy -var='db_master_password=<password>' -auto-approve"
    exit 1
fi

echo ""
echo "Destruction completed successfully!"
echo "All infrastructure has been removed from AWS."
echo ""
echo "NOTE: The Terraform state bucket 'sus-triagem-tfstate' was created manually"
echo "and was NOT deleted. To remove it run:"
echo "  aws s3 rm s3://sus-triagem-tfstate --recursive"
echo "  aws s3api delete-bucket --bucket sus-triagem-tfstate --region $AWS_REGION"
