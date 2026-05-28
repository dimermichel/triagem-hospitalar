# Rules Engine vs ML Model for Manchester Classification
---

## Context

The ms-manchester service needs to classify patients using the Manchester Protocol. Two approaches were considered: a hardcoded rules engine and a Machine Learning model.

## Decision

Start with a **rules engine** (`ManchesterEngine.java`) and design the architecture to support a future migration to ML.

## Rationale

| Criterion               | Rules Engine                          | ML Model                                    |
|-------------------------|---------------------------------------|---------------------------------------------|
| Delivery speed          | ✅ Weeks                              | ❌ Months (data collection, training, validation) |
| Clinical traceability   | ✅ 100% auditable                     | ⚠️ Black-box (explainability concerns)      |
| Medical validation      | ✅ Reviewed by nurses                 | ❌ Requires data labeled by clinicians      |
| Initial accuracy        | ✅ Based on the official protocol     | ✅ Potentially superior                     |
| Future evolution        | ⚠️ Hard to maintain 50 flowcharts    | ✅ Incremental training                     |

## Evolution Path

The `ManchesterEngine` interface abstracts the implementation. To add ML:

```java
// Current
@ApplicationScoped
public class ManchesterEngine {
    public CorManchester classificar(TriagemPayloadDTO p) { ... }
}

// Future ML version (same contract)
@Alternative @Priority(10)
@ApplicationScoped
public class ManchesterMLEngine extends ManchesterEngine {
    public CorManchester classificar(TriagemPayloadDTO p) {
        return callSageMakerEndpoint(p);
    }
}
```

Quarkus CDI selects the implementation via `@Alternative` without changing the handler.

## Training Data for the Future

The national database already collects `cor_manchester`, `queixa_principal`, `sinais_vitais`, and `tempo_espera_min`. After 6 months of operation, there will be sufficient data to train a model with medical review.
