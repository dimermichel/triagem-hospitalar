# Sistema de Triagem e Acolhimento Inteligente

Sistema de triagem hospitalar baseado no **Protocolo de Manchester**, desenvolvido como microsserviços serverless na AWS. Cada hospital recebe uma instância isolada, com dados consolidados ao final do dia em um banco nacional.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Terraform](https://img.shields.io/badge/Terraform-1.7+-purple.svg)](https://www.terraform.io/)
[![AWS](https://img.shields.io/badge/AWS-Lambda-orange.svg)](https://aws.amazon.com/lambda/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> **FIAP 2026 Java Tech Challenge** - Arquitetura de microsserviços serverless demonstrando design orientado a eventos, infraestrutura como código e práticas modernas de DevOps aplicadas à saúde.

---

## 🎯 Funcionalidades

### Funcionalidades Principais
- **API REST** para recepção de dados clínicos com validação
- **Protocolo de Manchester** - Classificação automática de risco por cor e prioridade
- **Painel Clínico em Tempo Real** - Interface React via WebSocket para acompanhamento da fila
- **Consolidação Diária** - Envio automatizado de atendimentos ao banco nacional
- **Isolamento por Hospital** - Cada unidade opera de forma independente com dados privativos

### Destaques Técnicos
- **Arquitetura Serverless** - Funções Lambda escaláveis e de baixo custo
- **Design Orientado a Eventos** - SQS para desacoplamento entre serviços
- **Infraestrutura como Código** - Deploy automatizado com Terraform
- **WebSocket** - Comunicação bidirecional em tempo real para o painel clínico
- **Banco Nacional** - Aurora PostgreSQL para consolidação centralizada

---

## 🏗️ Arquitetura

### Visão Geral do Sistema

```
Recepção → ms-triagem → SQS → ms-manchester → DynamoDB (local)
                                                   ↓
                                        Painel Clínico (WebSocket)
                                                   ↓
                                        ms-consolidador (job diário)
                                                   ↓
                                        SQS Nacional → RDS Aurora (nacional)
```

### Detalhes dos Componentes

| Serviço | Tecnologia | Responsabilidade |
|---|---|---|
| **ms-triagem** | Quarkus Lambda | API REST, validação de dados clínicos, envio para classificação |
| **ms-manchester** | Quarkus Lambda | Classificação de risco pelo Protocolo de Manchester |
| **ms-consolidador** | Quarkus Lambda | Job diário de consolidação e envio ao banco nacional |
| **ms-nacional-consumer** | Quarkus Lambda | Processamento dos dados consolidados no Aurora nacional |
| **ms-painel-ws** | Quarkus Lambda | Painel clínico em tempo real via WebSocket |
| **painel-clinico** | React (S3 + CloudFront) | Interface web em tempo real |
| **Banco Local** | DynamoDB | Armazenamento por hospital |
| **Banco Nacional** | Aurora PostgreSQL (RDS) | Consolidação centralizada |
| **Fila** | SQS | Desacoplamento assíncrono entre serviços |

---

## 🔴 Classificação de Risco (Manchester)

| Cor | Prioridade | Tempo Máximo de Atendimento |
|---|---|---|
| 🔴 Vermelho | Emergência | Imediato |
| 🟠 Laranja | Muito urgente | 10 min |
| 🟡 Amarelo | Urgente | 60 min |
| 🟢 Verde | Pouco urgente | 120 min |
| 🔵 Azul | Não urgente | 240 min |

---

## 🚀 Início Rápido

### Pré-requisitos
- Java 21+
- Maven 3.9+
- Node.js 20+
- Terraform 1.7+
- AWS CLI configurado
- Docker 24+ (para testes locais com LocalStack)
- `make`

### Desenvolvimento Local em 5 Minutos

```bash
# 1. Clone o repositório
git clone https://github.com/dimermichel/triagem-hospitalar.git
cd triagem-hospitalar

# 2. Sobe LocalStack + cria infraestrutura local (DynamoDB, SQS)
make dev-up

# 3. Em terminais separados, sobe cada serviço com hot reload:
make run-triagem      # porta 8080
make run-manchester   # porta 8081
make run-painel       # painel React

# 4. Testa a API
make api-test

# 5. Consulta a fila de pacientes no DynamoDB
make fila-status
```

Execute `make help` para ver todos os alvos disponíveis.

---

## 📡 Referência da API

### Registrar Triagem

**Endpoint:** `POST /triagem`

**Corpo da Requisição:**
```json
{
  "nome": "João Silva",
  "dataNascimento": "1985-03-15",
  "queixaPrincipal": "Dor no peito com irradiação para o braço esquerdo",
  "pressaoArterial": "160/100",
  "frequenciaCardiaca": 110,
  "temperatura": 37.2,
  "saturacaoO2": 94
}
```

**Resposta (200 OK):**
```json
{
  "pacienteId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "classificacao": "VERMELHO",
  "prioridade": "EMERGENCIA",
  "tempoMaximoAtendimento": "IMEDIATO",
  "mensagem": "Paciente encaminhado para atendimento imediato"
}
```

**Regras de Validação:**
- `nome`: Obrigatório, 3-100 caracteres
- `queixaPrincipal`: Obrigatório, 10-500 caracteres
- `frequenciaCardiaca`: Obrigatório, inteiro entre 30-250
- `saturacaoO2`: Obrigatório, inteiro entre 50-100

---

## 📂 Estrutura do Projeto

```
triagem-hospitalar/
├── ms-triagem/              # Microsserviço de coleta e validação (porta 8080)
├── ms-manchester/           # Microsserviço de classificação de risco (porta 8081)
├── ms-consolidador/         # Microsserviço de consolidação diária (porta 8082)
├── ms-nacional-consumer/    # Microsserviço consumidor do banco nacional (porta 8083)
├── ms-painel-ws/            # Microsserviço WebSocket do painel clínico (porta 8084)
├── painel-clinico/          # Frontend React (painel em tempo real)
├── shared/
│   ├── api-examples.sh      # Exemplos de chamadas à API (executar com make api-test)
│   └── schemas/
│       ├── triagem-evento.schema.json  # Schema JSON do evento de triagem
│       └── nacional.sql               # DDL do banco nacional Aurora
├── docs/
│   ├── ADR-001-arquitetura-serverless.md  # Decisão: Lambda + Quarkus vs alternativas
│   └── ADR-002-motor-regras-vs-ml.md      # Decisão: motor de regras vs modelo ML
├── infra/
│   ├── docker-compose.yml   # LocalStack para desenvolvimento local
│   ├── modules/
│   │   └── hospital/        # Módulo Terraform parametrizável por hospital_id
│   ├── environments/
│   │   ├── dev/             # Ambiente local (aponta para LocalStack)
│   │   └── prod/            # Ambiente de produção AWS
│   └── scripts/
│       ├── localstack-init.sh   # Inicialização do LocalStack
│       └── postgres-init.sql    # Bootstrap do Aurora nacional
├── Makefile                 # Todos os comandos de desenvolvimento e CI
└── CONTRIBUTING.md          # Guia de contribuição e convenções
```

---

## 💻 Stack Tecnológica

### Backend
- **Runtime:** Java 21
- **Framework:** Quarkus 3.x (otimizado para AWS Lambda)
- **Build:** Maven 3.9+

### AWS Services
- **Compute:** Lambda (5 funções)
- **API:** API Gateway (REST API + WebSocket)
- **Banco Local:** DynamoDB (por hospital)
- **Banco Nacional:** RDS Aurora PostgreSQL
- **Mensageria:** SQS (filas por hospital + fila nacional)
- **Frontend:** S3 + CloudFront

### Infraestrutura & DevOps
- **IaC:** Terraform 1.7+
- **Desenvolvimento Local:** LocalStack + Docker
- **Version Control:** Git com Gitflow
- **Monitoramento:** CloudWatch Logs & Metrics

---

## 🔧 Comandos Makefile

| Alvo | Descrição |
|---|---|
| `dev-up` | Sobe LocalStack e cria tabelas DynamoDB + filas SQS |
| `dev-down` | Para e remove os containers locais |
| `build` | Build de todos os microsserviços Java e do painel React |
| `build-native` | Build nativo GraalVM (menor cold start, mais lento) |
| `test` | Roda todos os testes (requer LocalStack rodando) |
| `test-manchester` | Roda apenas os testes do motor Manchester (rápido) |
| `run-triagem` | Sobe ms-triagem em modo dev (hot reload) |
| `run-manchester` | Sobe ms-manchester em modo dev |
| `run-painel` | Sobe o painel React em modo dev |
| `deploy-dev` | Deploy no ambiente dev (LocalStack via Terraform) |
| `tf-plan-prod` | Plano Terraform para produção (somente visualização) |
| `fmt` | Formata código Java (google-java-format) e Terraform |
| `api-test` | Executa exemplos de API contra o servidor local |
| `fila-status` | Lista pacientes na fila DynamoDB local |
| `logs-triagem` | Exibe logs do ms-triagem no CloudWatch |
| `clean` | Remove artefatos de build |

---

## 🏥 Deploy (por hospital)

```bash
# Ambiente de desenvolvimento (LocalStack)
make deploy-dev

# Produção — adicionar novo hospital em infra/environments/prod/main.tf:
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

## 🧪 Testes

### Testes Locais
```bash
# Build e execução de testes unitários
cd ms-manchester
./mvnw clean test

# Testes de integração (requer LocalStack)
make dev-up
make test
```

### Testes de Integração via API
```bash
# Consulta a fila após submeter triagem
curl -X POST http://localhost:8080/triagem \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Maria Oliveira",
    "queixaPrincipal": "Dor no peito intensa há 30 minutos",
    "frequenciaCardiaca": 120,
    "saturacaoO2": 91
  }'

# Verifica fila de atendimento
make fila-status
```

---

## 📊 Monitoramento & Observabilidade

### CloudWatch Logs
```bash
# Stream de logs de todos os serviços
make logs-triagem
aws logs tail /aws/lambda/ms-manchester-prod --follow
aws logs tail /aws/lambda/ms-consolidador-prod --follow
```

### Métricas para Monitorar
- Invocações, erros e duração das Lambdas
- Profundidade das filas SQS
- CPU, memória e conexões do Aurora
- Erros 4xx/5xx no API Gateway

---

## 🔄 Fluxo de Desenvolvimento

### Estratégia de Branches (Gitflow)
```
main (produção)
 └── develop (staging)
      ├── feature/nova-validacao
      ├── feature/dashboard-v2
      └── hotfix/bug-critico
```

### Workflow
```bash
# 1. Desenvolvimento de feature
git checkout -b feature/minha-feature develop
# ... implementa ...
git push origin feature/minha-feature
# Abre PR para develop

# 2. Release para produção
git checkout -b release/v1.1.0 develop
# Abre PR para main → deploy automático em produção

# 3. Hotfix
git checkout -b hotfix/issue-critico main
# Abre PR para main → deploy imediato em produção
```

---

## 📚 Documentação

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — Pré-requisitos, fluxo de desenvolvimento, convenções e como adicionar um novo hospital
- **[ADR-001](docs/ADR-001-arquitetura-serverless.md)** — Justificativa para Lambda + Quarkus vs ECS/K8s
- **[ADR-002](docs/ADR-002-motor-regras-vs-ml.md)** — Motor de regras Manchester vs modelo ML e caminho de evolução

---

## 🔐 Segurança

✅ **Implementado:**
- Isolamento de dados por hospital via DynamoDB separado
- Filas SQS com políticas de acesso por serviço
- IAM roles com privilégios mínimos por função Lambda
- HTTPS nos endpoints de API Gateway
- VPC com sub-redes privadas para Aurora nacional

⚠️ **Recomendações para Produção:**
- Migrar secrets para AWS Secrets Manager
- Habilitar criptografia em repouso no DynamoDB e Aurora
- Configurar AWS WAF para o API Gateway
- Implementar rate limiting por hospital
- Habilitar CloudTrail para auditoria
- Configurar backups multi-região para o Aurora nacional

---

## 🤝 Contribuindo

Contribuições são bem-vindas! Por favor, siga estas diretrizes:

1. **Fork** o repositório
2. Crie uma **branch de feature** (`git checkout -b feature/minha-feature`)
3. **Commit** suas mudanças (`git commit -m 'Add: nova funcionalidade'`)
4. **Push** para a branch (`git push origin feature/minha-feature`)
5. Abra um **Pull Request** para a branch `develop`

### Padrões de Contribuição
- Escreva mensagens de commit significativas
- Adicione testes para novas funcionalidades
- Atualize a documentação quando necessário
- Siga o estilo de código existente
- Garanta que os testes passem antes de abrir o PR

---

## 📄 Licença

Este projeto está licenciado sob a **MIT License** - veja o arquivo LICENSE para detalhes.

---

## 👥 Autores

**Michel Maia**
- GitHub: [@dimermichel](https://github.com/dimermichel)
- LinkedIn: [Connect with me](https://www.linkedin.com/in/dimermichel)
- Projeto: FIAP 2026 Java Tech Challenge

---

## 🙏 Agradecimentos

- **FIAP** pela oportunidade do Tech Challenge
- **AWS** pela infraestrutura serverless
- **Red Hat** pelo framework Quarkus
- **HashiCorp** pelo Terraform

---

## 📞 Suporte

- **Documentação:** Consulte [CONTRIBUTING.md](CONTRIBUTING.md) e os [ADRs](docs/)
- **Issues:** [GitHub Issues](https://github.com/dimermichel/triagem-hospitalar/issues)
- **Discussões:** [GitHub Discussions](https://github.com/dimermichel/triagem-hospitalar/discussions)

---

<div align="center">

**⭐ Se este projeto foi útil, considere deixar uma estrela!**

*Construído com dedicação para o FIAP 2026 Java Tech Challenge* 🚀

</div>
