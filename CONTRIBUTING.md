# Guia de Contribuição

## Pré-requisitos

| Ferramenta   | Versão mínima | Instalação                              |
|--------------|---------------|-----------------------------------------|
| Java         | 21            | [sdkman.io](https://sdkman.io)          |
| Maven        | 3.9           | `sdk install maven`                     |
| Node.js      | 20            | [nvm](https://github.com/nvm-sh/nvm)   |
| Docker       | 24            | [docker.com](https://docker.com)        |
| AWS CLI      | 2.x           | `brew install awscli`                   |
| Terraform    | 1.7+          | `brew install terraform`                |
| make         | qualquer      | `brew install make` / `apt install make`|

## Início rápido

```bash
git clone <repositório>
cd triagem-hospitalar

# 1. Sobe LocalStack e cria toda a infra local
make dev-up

# 2. Em outro terminal: sobe ms-triagem
make run-triagem

# 3. Em outro terminal: sobe ms-manchester
make run-manchester

# 4. Em outro terminal: sobe o painel
make run-painel

# 5. Testa a API
make api-test

# 6. Vê a fila DynamoDB
make fila-status
```

## Estrutura de branches

```
main         ← produção (protegida, requer PR + review)
develop      ← staging (integração contínua)
feature/*    ← novas funcionalidades
fix/*        ← correções
```

## Fluxo de desenvolvimento

1. Crie uma branch a partir de `develop`: `git checkout -b feature/minha-feature`
2. Desenvolva com hot reload: `make run-triagem`
3. Rode os testes: `make test`
4. Abra um PR para `develop`
5. O CI/CD valida automaticamente e faz deploy em staging
6. Após aprovação, merge em `main` → deploy em produção

## Convenções de código

- **Java:** Google Java Style Guide (`make fmt` aplica automaticamente)
- **Commits:** Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`)
- **Variáveis de ambiente:** todas em `UPPER_SNAKE_CASE`, documentadas no `application.properties`
- **Novos discriminadores Manchester:** adicionar em `ManchesterEngine.java` + teste correspondente em `ManchesterEngineTest.java`

## Adicionando um novo hospital

```bash
# 1. Edite infra/environments/prod/main.tf e adicione:
module "hosp_novo" {
  source             = "../../modules/hospital"
  hospital_id        = "nome-cidade"        # único e sem espaços
  env                = "prod"
  nacional_queue_url = aws_sqs_queue.nacional.url
}

# 2. Apply
cd infra/environments/prod
terraform apply -var="env=prod" -var="db_master_password=..."

# 3. Configure o painel:
VITE_HOSPITAL_NOME="Hospital Nome — Cidade"
VITE_API_URL="<output api_url do módulo>"
VITE_WS_URL="<output ws_url do módulo>"
```

## Variáveis de ambiente necessárias no GitHub

| Secret                   | Descrição                                  |
|--------------------------|--------------------------------------------|
| `AWS_ACCESS_KEY_ID`      | Chave AWS para staging                     |
| `AWS_SECRET_ACCESS_KEY`  | Secret AWS para staging                    |
| `DB_MASTER_PASSWORD`     | Senha do Aurora nacional (staging)         |
| `DB_MASTER_PASSWORD_PROD`| Senha do Aurora nacional (produção)        |
| `CLOUDFRONT_DIST_ID_PROD`| ID da distribuição CloudFront de produção  |
