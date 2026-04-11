# Arquitetura de Microsserviços Serverless com AWS Lambda + Quarkus
---

## Contexto

O sistema de triagem hospitalar precisa:
- Ser replicado para múltiplos hospitais com isolamento total de dados
- Suportar picos de chegada de pacientes (ex: emergências coletivas)
- Operar com custo mínimo fora dos horários de pico
- Ser implantado rapidamente por equipes com conhecimento Java

## Decisão

Adotar **AWS Lambda com Quarkus Serverless** como runtime de execução e **DynamoDB** como banco local por hospital.

## Alternativas Consideradas

| Alternativa            | Prós                             | Contras                                  |
|------------------------|----------------------------------|------------------------------------------|
| ECS Fargate + RDS      | Controle total, SQL familiar     | Custo fixo alto, complexidade de escala  |
| Lambda + Quarkus (escolha) | Scale-to-zero, deploy rápido | Cold start (mitigado com SnapStart)      |
| Spring Boot + Lambda   | Ecossistema maior                | Cold start muito alto (5-10s sem nativo) |
| K8s + Helm             | Portabilidade                    | Complexidade operacional alta para MVPs  |

## Consequências

**Positivas:**
- Custo zero fora do horário de operação
- Escala automática em emergências coletivas
- SnapStart do Java 21 reduz cold start de 8s para ~1s
- Módulo Terraform parametrizado por `hospital_id` garante replicabilidade

**Negativas:**
- Limite de 15 min de execução (adequado para o ms-consolidador com timeout de 5 min)
- SQS com visibilidade de 30–60s requer idempotência no ms-manchester
- WebSocket via API Gateway tem limite de 2h por conexão (client deve reconectar)

## Mitigações

- `SnapStart = PublishedVersions` em todas as Lambdas Java
- DLQ em todas as filas SQS com alarme CloudWatch
- `ON CONFLICT DO NOTHING` no ms-nacional-consumer para idempotência
- Reconexão automática no `useFilaWebSocket` do painel React
