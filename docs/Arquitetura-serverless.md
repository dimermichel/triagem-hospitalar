# Serverless Microservices Architecture with AWS Lambda + Quarkus
---

## Context

The hospital triage system needs to:
- Be replicated across multiple hospitals with full data isolation
- Handle spikes in patient arrivals (e.g., mass casualty events)
- Operate at minimal cost outside peak hours
- Be deployed quickly by teams with Java expertise

## Decision

Adopt **AWS Lambda with Quarkus Serverless** as the execution runtime and **DynamoDB** as the local database per hospital.

## Considered Alternatives

| Alternative              | Pros                              | Cons                                        |
|--------------------------|-----------------------------------|---------------------------------------------|
| ECS Fargate + RDS        | Full control, familiar SQL        | High fixed cost, complex scaling            |
| Lambda + Quarkus (chosen)| Scale-to-zero, fast deployment    | Cold start (mitigated with SnapStart)       |
| Spring Boot + Lambda     | Larger ecosystem                  | Very high cold start (5–10s without native) |
| K8s + Helm               | Portability                       | High operational complexity for MVPs        |

## Consequences

**Positive:**
- Zero cost outside operating hours
- Automatic scaling during mass casualty events
- Java 21 SnapStart reduces cold start from 8s to ~1s
- Terraform module parameterized by `hospital_id` ensures replicability

**Negative:**
- 15-minute execution limit (acceptable for ms-consolidador with a 5-minute timeout)
- SQS visibility window of 30–60s requires idempotency in ms-manchester
- WebSocket via API Gateway has a 2-hour connection limit (client must reconnect)

## Mitigations

- `SnapStart = PublishedVersions` on all Java Lambdas
- DLQ on all SQS queues with a CloudWatch alarm
- `ON CONFLICT DO NOTHING` in ms-nacional-consumer for idempotency
- Automatic reconnection in `useFilaWebSocket` in the React dashboard
