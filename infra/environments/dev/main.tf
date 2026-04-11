terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
  # Dev usa backend local para agilidade
  backend "local" {
    path = "terraform.tfstate"
  }
}

provider "aws" {
  region = "us-east-1"

  # Aponta para LocalStack em dev
  endpoints {
    dynamodb   = "http://localhost:4566"
    sqs        = "http://localhost:4566"
    lambda     = "http://localhost:4566"
    apigateway = "http://localhost:4566"
    iam        = "http://localhost:4566"
    logs       = "http://localhost:4566"
    eventbridge = "http://localhost:4566"
  }

  access_key = "test"
  secret_key = "test"

  skip_credentials_validation = true
  skip_requesting_account_id  = true
  skip_metadata_api_check     = true
}

# Fila nacional simulada (em dev, fica local)
resource "aws_sqs_queue" "nacional_dev" {
  name = "dev-sus-triagem-nacional"
}

# Instância única de hospital para dev
module "hosp_dev" {
  source             = "../../modules/hospital"
  hospital_id        = "hosp-dev"
  env                = "dev"
  hospital_nome      = "Hospital Local Dev"
  aws_region         = "us-east-1"
  nacional_queue_url = aws_sqs_queue.nacional_dev.url
  nacional_queue_arn = aws_sqs_queue.nacional_dev.arn
  lambda_memory_mb   = 256
  lambda_timeout_sec = 30
  enable_frontend    = false
}

output "api_url" {
  value = module.hosp_dev.api_url
}

output "ws_url" {
  value = "Use wscat -c ws://localhost:4566/dev?hospital_id=hosp-dev"
}
