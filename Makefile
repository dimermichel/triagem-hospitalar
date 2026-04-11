# ============================================================
# Makefile — Sistema de Triagem Hospitalar
# ============================================================
# Uso: make <alvo>
# Requer: Java 21+, Maven, Node 20+, Docker, AWS CLI, Terraform
# ============================================================

.PHONY: help dev-up dev-down build test deploy-dev clean fmt purge-classificacao

HOSPITAL_ID ?= hosp-local-dev
AWS_ENDPOINT  = http://localhost:4566
AWS_REGION    = us-east-1
AWS_FLAGS     = --endpoint-url=$(AWS_ENDPOINT) --region $(AWS_REGION) \
                --no-cli-pager
export AWS_ACCESS_KEY_ID  = test
export AWS_SECRET_ACCESS_KEY = test

# ── Ajuda ─────────────────────────────────────────────────────────────────────
help: ## Exibe este menu
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}'

# ── Infraestrutura local ───────────────────────────────────────────────────────
dev-up: ## Sobe LocalStack + painel React em background
	@echo "▶ Subindo LocalStack..."
	docker compose -f infra/docker-compose.yml up -d
	@echo "⏳ Aguardando LocalStack ficar saudável..."
	@until curl -sf $(AWS_ENDPOINT)/_localstack/health | grep -q '"dynamodb": "available"'; do \
		sleep 2; \
	done
	@echo "✅ LocalStack pronto"
	@$(MAKE) dev-infra

dev-down: ## Para e remove os containers locais
	docker compose -f infra/docker-compose.yml down -v
	@echo "✅ Ambiente local removido"

dev-infra: ## Cria tabelas DynamoDB e filas SQS no LocalStack
	@echo "▶ Criando DynamoDB: triagem-local..."
	@aws $(AWS_FLAGS) dynamodb create-table \
		--table-name triagem-local \
		--attribute-definitions \
			AttributeName=pk,AttributeType=S \
			AttributeName=sk,AttributeType=S \
		--key-schema \
			AttributeName=pk,KeyType=HASH \
			AttributeName=sk,KeyType=RANGE \
		--billing-mode PAY_PER_REQUEST 2>/dev/null || true

	@echo "▶ Criando DynamoDB: conexoes-local..."
	@aws $(AWS_FLAGS) dynamodb create-table \
		--table-name conexoes-local \
		--attribute-definitions \
			AttributeName=connectionId,AttributeType=S \
			AttributeName=hospitalId,AttributeType=S \
		--key-schema AttributeName=connectionId,KeyType=HASH \
		--global-secondary-indexes '[{"IndexName":"hospital-index","KeySchema":[{"AttributeName":"hospitalId","KeyType":"HASH"}],"Projection":{"ProjectionType":"ALL"}}]' \
		--billing-mode PAY_PER_REQUEST 2>/dev/null || true

	@echo "▶ Criando filas SQS..."
	@for q in classificacao-queue painel-queue nacional-queue \
	           classificacao-queue-dlq painel-queue-dlq nacional-queue-dlq; do \
		aws $(AWS_FLAGS) sqs create-queue --queue-name $$q 2>/dev/null || true; \
	done
	@echo "✅ Infraestrutura local pronta"

# ── Build ──────────────────────────────────────────────────────────────────────
build: ## Build de todos os microsserviços Java
	@echo "▶ Build ms-triagem..."
	cd ms-triagem && ./mvnw package -DskipTests -q
	@echo "▶ Build ms-manchester..."
	cd ms-manchester && ./mvnw package -DskipTests -q
	@echo "▶ Build ms-consolidador..."
	cd ms-consolidador && ./mvnw package -DskipTests -q
	@echo "▶ Build ms-painel-ws..."
	cd ms-painel-ws && ./mvnw package -DskipTests -q
	@echo "▶ Build ms-nacional-consumer..."
	cd ms-nacional-consumer && ./mvnw package -DskipTests -q
	@echo "▶ Build painel-clinico..."
	cd painel-clinico && npm ci && npm run build
	@echo "✅ Todos os builds concluídos"

build-native: ## Build nativo GraalVM (mais lento, menor cold start)
	@echo "▶ Build nativo — pode levar 10+ minutos por serviço"
	for svc in ms-triagem ms-manchester ms-consolidador ms-painel-ws ms-nacional-consumer; do \
		cd $$svc && ./mvnw package -Pnative -DskipTests -q && cd ..; \
	done
	@echo "✅ Builds nativos concluídos"

# ── Testes ────────────────────────────────────────────────────────────────────
test: ## Roda todos os testes (requer LocalStack rodando)
	@echo "▶ Testes ms-manchester (sem dependências externas)..."
	cd ms-manchester && ./mvnw test -B
	@echo "▶ Testes ms-triagem..."
	cd ms-triagem && ./mvnw test -B \
		-DQUARKUS_DYNAMODB_ENDPOINT_OVERRIDE=$(AWS_ENDPOINT) \
		-DQUARKUS_SQS_ENDPOINT_OVERRIDE=$(AWS_ENDPOINT) \
		-DHOSPITAL_ID=$(HOSPITAL_ID)
	@echo "▶ Testes ms-consolidador..."
	cd ms-consolidador && ./mvnw test -B \
		-DQUARKUS_DYNAMODB_ENDPOINT_OVERRIDE=$(AWS_ENDPOINT) \
		-DQUARKUS_SQS_ENDPOINT_OVERRIDE=$(AWS_ENDPOINT) \
		-DHOSPITAL_ID=$(HOSPITAL_ID)
	@echo "▶ Testes ms-nacional-consumer (H2 in-memory)..."
	cd ms-nacional-consumer && ./mvnw test -B -Dquarkus.profile=test
	@echo "✅ Todos os testes passaram"

test-manchester: ## Roda apenas os testes do motor Manchester (rápido)
	cd ms-manchester && ./mvnw test -B

# ── Dev mode (hot reload) ──────────────────────────────────────────────────────
run-triagem: ## Sobe ms-triagem em modo dev (hot reload)
	cd ms-triagem && ./mvnw quarkus:dev \
		-Dquarkus.dynamodb.endpoint-override=$(AWS_ENDPOINT) \
		-Dquarkus.sqs.endpoint-override=$(AWS_ENDPOINT) \
		-Dhospital.id=$(HOSPITAL_ID)

run-manchester: ## Sobe ms-manchester em modo dev
	cd ms-manchester && ./mvnw quarkus:dev -Dquarkus.http.port=8081 \
		-Dquarkus.dynamodb.endpoint-override=$(AWS_ENDPOINT) \
		-Dquarkus.sqs.endpoint-override=$(AWS_ENDPOINT) \
		-Dhospital.id=$(HOSPITAL_ID)

run-painel: ## Sobe o painel React em modo dev
	cd painel-clinico && npm run dev

# ── Infraestrutura AWS (dev) ───────────────────────────────────────────────────
deploy-dev: build ## Deploy no ambiente dev (LocalStack via Terraform)
	cd infra/environments/dev && \
		terraform init -reconfigure && \
		terraform apply -auto-approve
	@echo "✅ Deploy dev concluído"

tf-bootstrap: ## Cria bucket S3 e habilita versionamento/criptografia para o backend Terraform
	@echo "▶ Criando bucket S3 para Terraform state..."
	aws s3api create-bucket \
		--bucket sus-triagem-tfstate \
		--region $(AWS_REGION)
	aws s3api put-bucket-versioning \
		--bucket sus-triagem-tfstate \
		--versioning-configuration Status=Enabled
	aws s3api put-bucket-encryption \
		--bucket sus-triagem-tfstate \
		--server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
	@echo "✅ Backend Terraform pronto — agora rode: make tf-plan-prod"

tf-plan-prod: ## Plano Terraform para produção (somente visualização)
	cd infra/environments/prod && \
		terraform init && \
		terraform plan -var="env=prod" -var="db_master_password=REDACTED"

# ── Utilitários ───────────────────────────────────────────────────────────────
fmt: ## Formata código Java (google-java-format) e Terraform
	@for svc in ms-triagem ms-manchester ms-consolidador ms-painel-ws ms-nacional-consumer; do \
		cd $$svc && ./mvnw spotless:apply -q 2>/dev/null || true && cd ..; \
	done
	terraform fmt -recursive infra/

logs-triagem: ## Exibe logs do ms-triagem no CloudWatch (requer AWS configurado)
	aws logs tail /aws/lambda/$(HOSPITAL_ID)-ms-triagem --follow

api-test: ## Executa os exemplos de API contra o servidor local
	API_URL=http://localhost:8080 bash shared/api-examples.sh

purge-classificacao: ## Remove pacientes presos em AGUARDANDO_CLASSIFICACAO do DynamoDB local
	@echo "▶ Buscando pacientes presos em AGUARDANDO_CLASSIFICACAO..."
	@aws $(AWS_FLAGS) dynamodb query \
		--table-name triagem-local \
		--key-condition-expression "pk = :pk" \
		--filter-expression "#s = :status" \
		--expression-attribute-names '{"#s":"status"}' \
		--expression-attribute-values '{":pk":{"S":"HOSPITAL#$(HOSPITAL_ID)#FILA"},":status":{"S":"AGUARDANDO_CLASSIFICACAO"}}' \
		--query 'Items[*].{pk:pk.S,sk:sk.S}' \
		--output json \
	| jq -r '.[] | @base64' \
	| while read item; do \
		pk=$$(echo $$item | base64 -d | jq -r '.pk'); \
		sk=$$(echo $$item | base64 -d | jq -r '.sk'); \
		echo "  🗑  Removendo sk=$$sk"; \
		aws $(AWS_FLAGS) dynamodb delete-item \
			--table-name triagem-local \
			--key "{\"pk\":{\"S\":\"$$pk\"},\"sk\":{\"S\":\"$$sk\"}}"; \
	done
	@echo "✅ Pacientes presos removidos"

fila-status: ## Lista pacientes na fila DynamoDB local
	@aws $(AWS_FLAGS) dynamodb query \
		--table-name triagem-local \
		--key-condition-expression "pk = :pk" \
		--expression-attribute-values '{":pk":{"S":"HOSPITAL#$(HOSPITAL_ID)#FILA"}}' \
		--output table

clean: ## Remove artefatos de build
	@for svc in ms-triagem ms-manchester ms-consolidador ms-painel-ws ms-nacional-consumer; do \
		rm -rf $$svc/target; \
	done
	rm -rf painel-clinico/dist painel-clinico/node_modules
	@echo "✅ Limpeza concluída"
