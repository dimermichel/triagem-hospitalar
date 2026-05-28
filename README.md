# Intelligent Triage and Reception System

Hospital triage system based on the **Manchester Protocol**, built as serverless microservices on AWS. Each hospital gets an isolated instance, with data consolidated at the end of the day into a national database.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Terraform](https://img.shields.io/badge/Terraform-1.7+-purple.svg)](https://www.terraform.io/)
[![AWS](https://img.shields.io/badge/AWS-Lambda-orange.svg)](https://aws.amazon.com/lambda/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> **FIAP 2026 Java Tech Challenge** - Serverless microservices architecture demonstrating event-driven design, infrastructure as code, and modern DevOps practices applied to healthcare.

---

## 🎯 Features

### Core Features
- **REST API** for receiving clinical data with validation
- **Manchester Protocol** - Automatic risk classification by color and priority
- **Real-Time Clinical Dashboard** - React interface via WebSocket for queue monitoring
- **Daily Consolidation** - Automated submission of records to the national database
- **Hospital Isolation** - Each unit operates independently with private data

### Technical Highlights
- **Serverless Architecture** - Scalable, low-cost Lambda functions
- **Event-Driven Design** - SQS for decoupling between services
- **Infrastructure as Code** - Automated deployment with Terraform
- **WebSocket** - Real-time bidirectional communication for the clinical dashboard
- **National Database** - Aurora PostgreSQL for centralized consolidation

---

## 🏗️ Architecture

### System Overview

```
Reception → ms-triagem → SQS → ms-manchester → DynamoDB (local)
                                                   ↓
                                        Clinical Dashboard (WebSocket)
                                                   ↓
                                        ms-consolidador (daily job)
                                                   ↓
                                        National SQS → Aurora RDS (national)
```

### Component Details

| Service | Technology | Responsibility |
|---|---|---|
| **ms-triagem** | Quarkus Lambda | REST API, clinical data validation, dispatch for classification |
| **ms-manchester** | Quarkus Lambda | Risk classification using the Manchester Protocol |
| **ms-consolidador** | Quarkus Lambda | Daily consolidation job and submission to national database |
| **ms-nacional-consumer** | Quarkus Lambda | Processing consolidated data in national Aurora |
| **ms-painel-ws** | Quarkus Lambda | Real-time clinical dashboard via WebSocket |
| **painel-clinico** | React (S3 + CloudFront) | Real-time web interface |
| **Local Database** | DynamoDB | Per-hospital storage |
| **National Database** | Aurora PostgreSQL (RDS) | Centralized consolidation |
| **Queue** | SQS | Asynchronous decoupling between services |

---

## 🔴 Risk Classification (Manchester)

| Color | Priority | Maximum Care Time |
|---|---|---|
| 🔴 Red | Emergency | Immediate |
| 🟠 Orange | Very urgent | 10 min |
| 🟡 Yellow | Urgent | 60 min |
| 🟢 Green | Less urgent | 120 min |
| 🔵 Blue | Non-urgent | 240 min |

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Node.js 20+
- Terraform 1.7+
- AWS CLI configured
- Docker 24+ (for local testing with LocalStack)
- `make`

### Local Development in 5 Minutes

```bash
# 1. Clone the repository
git clone https://github.com/dimermichel/triagem-hospitalar.git
cd triagem-hospitalar

# 2. Start LocalStack + create local infrastructure (DynamoDB, SQS)
make dev-up

# 3. In separate terminals, start each service with hot reload:
make run-triagem      # port 8080
make run-manchester   # port 8081
make run-painel       # React dashboard

# 4. Test the API
make api-test

# 5. Check the patient queue in DynamoDB
make fila-status
```

Run `make help` to see all available targets.

---

## 📡 API Reference

### Register Triage

**Endpoint:** `POST /triagem`

**Request Body:**
```json
{
  "nome": "João Silva",
  "dataNascimento": "1985-03-15",
  "queixaPrincipal": "Chest pain radiating to the left arm",
  "pressaoArterial": "160/100",
  "frequenciaCardiaca": 110,
  "temperatura": 37.2,
  "saturacaoO2": 94
}
```

**Response (200 OK):**
```json
{
  "pacienteId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "classificacao": "VERMELHO",
  "prioridade": "EMERGENCIA",
  "tempoMaximoAtendimento": "IMEDIATO",
  "mensagem": "Patient referred for immediate care"
}
```

**Validation Rules:**
- `nome`: Required, 3–100 characters
- `queixaPrincipal`: Required, 10–500 characters
- `frequenciaCardiaca`: Required, integer between 30–250
- `saturacaoO2`: Required, integer between 50–100

---

## 📂 Project Structure

```
triagem-hospitalar/
├── ms-triagem/              # Data collection and validation microservice (port 8080)
├── ms-manchester/           # Risk classification microservice (port 8081)
├── ms-consolidador/         # Daily consolidation microservice (port 8082)
├── ms-nacional-consumer/    # National database consumer microservice (port 8083)
├── ms-painel-ws/            # Clinical dashboard WebSocket microservice (port 8084)
├── painel-clinico/          # React frontend (real-time dashboard)
├── shared/
│   ├── api-examples.sh      # API call examples (run with make api-test)
│   └── schemas/
│       ├── triagem-evento.schema.json  # JSON schema for triage event
│       └── nacional.sql               # National Aurora database DDL
├── docs/
│   ├── ADR-001-arquitetura-serverless.md  # Decision: Lambda + Quarkus vs alternatives
│   └── ADR-002-motor-regras-vs-ml.md      # Decision: rules engine vs ML model
├── infra/
│   ├── docker-compose.yml   # LocalStack for local development
│   ├── modules/
│   │   └── hospital/        # Terraform module parameterizable by hospital_id
│   ├── environments/
│   │   ├── dev/             # Local environment (points to LocalStack)
│   │   └── prod/            # AWS production environment
│   └── scripts/
│       ├── localstack-init.sh   # LocalStack initialization
│       └── postgres-init.sql    # National Aurora bootstrap
├── Makefile                 # All development and CI commands
└── CONTRIBUTING.md          # Contribution guide and conventions
```

---

## 💻 Technology Stack

### Backend
- **Runtime:** Java 21
- **Framework:** Quarkus 3.x (optimized for AWS Lambda)
- **Build:** Maven 3.9+

### AWS Services
- **Compute:** Lambda (5 functions)
- **API:** API Gateway (REST API + WebSocket)
- **Local Database:** DynamoDB (per hospital)
- **National Database:** RDS Aurora PostgreSQL
- **Messaging:** SQS (per-hospital queues + national queue)
- **Frontend:** S3 + CloudFront

### Infrastructure & DevOps
- **IaC:** Terraform 1.7+
- **Local Development:** LocalStack + Docker
- **Version Control:** Git with Gitflow
- **Monitoring:** CloudWatch Logs & Metrics

---

## 🔧 Makefile Commands

| Target | Description |
|---|---|
| `dev-up` | Start LocalStack and create DynamoDB tables + SQS queues |
| `dev-down` | Stop and remove local containers |
| `build` | Build all Java microservices and the React dashboard |
| `build-native` | GraalVM native build (faster cold start, slower to build) |
| `test` | Run all tests (requires LocalStack running) |
| `test-manchester` | Run Manchester engine tests only (fast) |
| `run-triagem` | Start ms-triagem in dev mode (hot reload) |
| `run-manchester` | Start ms-manchester in dev mode |
| `run-painel` | Start the React dashboard in dev mode |
| `deploy-dev` | Deploy to dev environment (LocalStack via Terraform) |
| `tf-plan-prod` | Terraform plan for production (view only) |
| `fmt` | Format Java code (google-java-format) and Terraform |
| `api-test` | Run API examples against the local server |
| `fila-status` | List patients in the local DynamoDB queue |
| `logs-triagem` | Stream ms-triagem logs from CloudWatch |
| `clean` | Remove build artifacts |

---

## 🏥 Deployment (per hospital)

```bash
# Development environment (LocalStack)
make deploy-dev

# Production — add a new hospital in infra/environments/prod/main.tf:
module "hosp_xpto_sp" {
  source             = "../../modules/hospital"
  hospital_id        = "hosp-xpto-sp"
  env                = "prod"
  nacional_queue_url = aws_sqs_queue.nacional.url
}

cd infra/environments/prod
terraform apply -var="env=prod" -var="db_master_password=..."
```

---

## 🧪 Testing

### Local Tests
```bash
# Build and run unit tests
cd ms-manchester
./mvnw clean test

# Integration tests (requires LocalStack)
make dev-up
make test
```

### API Integration Tests
```bash
# Check the queue after submitting a triage
curl -X POST http://localhost:8080/triagem \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Maria Oliveira",
    "queixaPrincipal": "Intense chest pain for the past 30 minutes",
    "frequenciaCardiaca": 120,
    "saturacaoO2": 91
  }'

# Check the care queue
make fila-status
```

---

## 📊 Monitoring & Observability

### CloudWatch Logs
```bash
# Stream logs from all services
make logs-triagem
aws logs tail /aws/lambda/ms-manchester-prod --follow
aws logs tail /aws/lambda/ms-consolidador-prod --follow
```

### Metrics to Monitor
- Lambda invocations, errors, and duration
- SQS queue depth
- Aurora CPU, memory, and connections
- API Gateway 4xx/5xx errors

---

## 🔄 Development Workflow

### Branch Strategy (Gitflow)
```
main (production)
 └── develop (staging)
      ├── feature/new-validation
      ├── feature/dashboard-v2
      └── hotfix/critical-bug
```

### Workflow
```bash
# 1. Feature development
git checkout -b feature/my-feature develop
# ... implement ...
git push origin feature/my-feature
# Open PR to develop

# 2. Release to production
git checkout -b release/v1.1.0 develop
# Open PR to main → automatic deploy to production

# 3. Hotfix
git checkout -b hotfix/critical-issue main
# Open PR to main → immediate deploy to production
```

---

## 📚 Documentation

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — Prerequisites, development workflow, conventions, and how to add a new hospital
- **[ADR-001](docs/ADR-001-arquitetura-serverless.md)** — Rationale for Lambda + Quarkus vs ECS/K8s
- **[ADR-002](docs/ADR-002-motor-regras-vs-ml.md)** — Manchester rules engine vs ML model and evolution path

---

## 🔐 Security

✅ **Implemented:**
- Per-hospital data isolation via separate DynamoDB instances
- SQS queues with per-service access policies
- IAM roles with least-privilege per Lambda function
- HTTPS on API Gateway endpoints
- VPC with private subnets for national Aurora

⚠️ **Production Recommendations:**
- Migrate secrets to AWS Secrets Manager
- Enable encryption at rest for DynamoDB and Aurora
- Configure AWS WAF for API Gateway
- Implement per-hospital rate limiting
- Enable CloudTrail for auditing
- Configure multi-region backups for national Aurora

---

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork** the repository
2. Create a **feature branch** (`git checkout -b feature/my-feature`)
3. **Commit** your changes (`git commit -m 'Add: new feature'`)
4. **Push** to the branch (`git push origin feature/my-feature`)
5. Open a **Pull Request** to the `develop` branch

### Contribution Standards
- Write meaningful commit messages
- Add tests for new features
- Update documentation when necessary
- Follow the existing code style
- Ensure tests pass before opening a PR

---

## 📄 License

This project is licensed under the **MIT License** - see the LICENSE file for details.

---

## 👥 Authors

**Michel Maia**
- GitHub: [@dimermichel](https://github.com/dimermichel)
- LinkedIn: [Connect with me](https://www.linkedin.com/in/dimermichel)
- Project: FIAP 2026 Java Tech Challenge

---

## 🙏 Acknowledgements

- **FIAP** for the Tech Challenge opportunity
- **AWS** for the serverless infrastructure
- **Red Hat** for the Quarkus framework
- **HashiCorp** for Terraform

---

## 📞 Support

- **Documentation:** See [CONTRIBUTING.md](CONTRIBUTING.md) and the [ADRs](docs/)
- **Issues:** [GitHub Issues](https://github.com/dimermichel/triagem-hospitalar/issues)
- **Discussions:** [GitHub Discussions](https://github.com/dimermichel/triagem-hospitalar/discussions)

---

<div align="center">

**⭐ If this project was useful, consider leaving a star!**

*Built with dedication for the FIAP 2026 Java Tech Challenge* 🚀

</div>
