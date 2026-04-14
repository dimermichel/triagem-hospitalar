terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }

  backend "s3" {
    bucket         = "sus-triagem-tfstate"
    key            = "prod/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    use_lockfile = true
  }
}

provider "aws" {
  region = "us-east-1"
}

# ─────────────────────────────────────────────
# Banco de dados nacional (compartilhado)
# ─────────────────────────────────────────────
resource "aws_rds_cluster" "nacional" {
  cluster_identifier      = "sus-triagem-nacional"
  engine                  = "aurora-postgresql"
  engine_mode             = "provisioned"
  engine_version          = "16.6"
  database_name           = "triagem_nacional"
  master_username         = "susadmin"
  master_password         = var.db_master_password
  skip_final_snapshot     = false
  final_snapshot_identifier = "sus-triagem-final-snapshot"
  deletion_protection     = var.deletion_protection

  # Permite acesso do Lambda ms-nacional-consumer (definido em nacional-consumer.tf)
  vpc_security_group_ids = [aws_security_group.nacional_rds.id]

  serverlessv2_scaling_configuration {
    min_capacity = 0.5
    max_capacity = 4.0
  }

  tags = { ManagedBy = "terraform", System = "triagem-hospitalar", Env = "prod" }
}

resource "aws_rds_cluster_instance" "nacional" {
  identifier         = "sus-triagem-nacional-instance-1"
  cluster_identifier = aws_rds_cluster.nacional.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.nacional.engine
  engine_version     = aws_rds_cluster.nacional.engine_version

  tags = { ManagedBy = "terraform", System = "triagem-hospitalar", Env = "prod" }
}

# Fila SQS nacional — recebe consolidados de todos os hospitais
resource "aws_sqs_queue" "nacional" {
  name                       = "prod-sus-triagem-nacional"
  visibility_timeout_seconds = 300
  message_retention_seconds  = 86400

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.nacional_dlq.arn
    maxReceiveCount     = 5
  })

  tags = { ManagedBy = "terraform", System = "triagem-hospitalar" }
}

resource "aws_sqs_queue" "nacional_dlq" {
  name                      = "prod-sus-triagem-nacional-dlq"
  message_retention_seconds = 1209600
}

# ─────────────────────────────────────────────
# Instâncias de hospital
# ─────────────────────────────────────────────
module "hosp_einstein_sp" {
  source             = "../../modules/hospital"
  hospital_id        = "einstein-sp"
  env                = "prod"
  hospital_nome      = "Hospital Einstein - SP"
  nacional_queue_url = aws_sqs_queue.nacional.url
  nacional_queue_arn = aws_sqs_queue.nacional.arn
}

module "hosp_hc_sp" {
  source             = "../../modules/hospital"
  hospital_id        = "hc-sp"
  env                = "prod"
  hospital_nome      = "Hospital das Clínicas - SP"
  nacional_queue_url = aws_sqs_queue.nacional.url
  nacional_queue_arn = aws_sqs_queue.nacional.arn
}

# Para adicionar um novo hospital, basta duplicar o bloco acima:
# module "hosp_novo" {
#   source             = "../../modules/hospital"
#   hospital_id        = "nome-cidade"
#   env                = "prod"
#   nacional_queue_url = aws_sqs_queue.nacional.url
# }

variable "env" {
  description = "Nome do ambiente (dev, prod)"
  type        = string
  default     = "prod"
}

variable "db_master_password" {
  description = "Senha do banco nacional (use AWS Secrets Manager em produção)"
  type        = string
  sensitive   = true
}

variable "deletion_protection" {
  description = "Habilita proteção contra deleção do cluster RDS. Defina false antes de destruir."
  type        = bool
  default     = true
}
