# Contribution Guide

## Prerequisites

| Tool       | Minimum version | Installation                            |
|------------|----------------|-----------------------------------------|
| Java       | 21             | [sdkman.io](https://sdkman.io)          |
| Maven      | 3.9            | `sdk install maven`                     |
| Node.js    | 20             | [nvm](https://github.com/nvm-sh/nvm)   |
| Docker     | 24             | [docker.com](https://docker.com)        |
| AWS CLI    | 2.x            | `brew install awscli`                   |
| Terraform  | 1.7+           | `brew install terraform`                |
| make       | any            | `brew install make` / `apt install make`|

## Quick Start

```bash
git clone <repository>
cd triagem-hospitalar

# 1. Start LocalStack and create all local infrastructure
make dev-up

# 2. In another terminal: start ms-triagem
make run-triagem

# 3. In another terminal: start ms-manchester
make run-manchester

# 4. In another terminal: start the dashboard
make run-painel

# 5. Test the API
make api-test

# 6. Check the DynamoDB queue
make fila-status
```

## Branch Structure

```
main         ← production (protected, requires PR + review)
develop      ← staging (continuous integration)
feature/*    ← new features
fix/*        ← bug fixes
```

## Development Workflow

1. Create a branch from `develop`: `git checkout -b feature/my-feature`
2. Develop with hot reload: `make run-triagem`
3. Run tests: `make test`
4. Open a PR to `develop`
5. CI/CD automatically validates and deploys to staging
6. After approval, merge into `main` → deploy to production

## Code Conventions

- **Java:** Google Java Style Guide (`make fmt` applies it automatically)
- **Commits:** Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`)
- **Environment variables:** all in `UPPER_SNAKE_CASE`, documented in `application.properties`
- **New Manchester discriminators:** add in `ManchesterEngine.java` + corresponding test in `ManchesterEngineTest.java`

## Adding a New Hospital

```bash
# 1. Edit infra/environments/prod/main.tf and add:
module "hosp_novo" {
  source             = "../../modules/hospital"
  hospital_id        = "city-name"          # unique, no spaces
  env                = "prod"
  nacional_queue_url = aws_sqs_queue.nacional.url
}

# 2. Apply
cd infra/environments/prod
terraform apply -var="env=prod" -var="db_master_password=..."

# 3. Configure the dashboard:
VITE_HOSPITAL_NOME="Hospital Name — City"
VITE_API_URL="<api_url output from the module>"
VITE_WS_URL="<ws_url output from the module>"
```

## Required GitHub Environment Variables

| Secret                   | Description                                      |
|--------------------------|--------------------------------------------------|
| `AWS_ACCESS_KEY_ID`      | AWS key for staging                              |
| `AWS_SECRET_ACCESS_KEY`  | AWS secret for staging                           |
| `DB_MASTER_PASSWORD`     | National Aurora password (staging)               |
| `DB_MASTER_PASSWORD_PROD`| National Aurora password (production)            |
| `CLOUDFRONT_DIST_ID_PROD`| Production CloudFront distribution ID            |
