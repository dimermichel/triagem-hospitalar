# Motor de Regras vs Modelo ML para Classificação Manchester
---

## Contexto

O ms-manchester precisa classificar pacientes no Protocolo de Manchester. Duas abordagens foram consideradas: motor de regras codificado e modelo de Machine Learning.

## Decisão

Iniciar com **motor de regras** (`ManchesterEngine.java`) e preparar a arquitetura para evolução para ML.

## Justificativa

| Critério               | Motor de Regras             | Modelo ML                        |
|------------------------|-----------------------------|----------------------------------|
| Velocidade de entrega  | ✅ Semanas                  | ❌ Meses (coleta, treino, validação) |
| Rastreabilidade clínica | ✅ 100% auditável           | ⚠️ Caixa-preta (explainability)  |
| Validação médica       | ✅ Revisado por enfermeiros  | ❌ Requer dados rotulados por CRM |
| Precisão inicial       | ✅ Baseada no protocolo oficial | ✅ Potencialmente superior       |
| Evolução futura        | ⚠️ Difícil manter 50 fluxogramas | ✅ Treino incremental         |

## Caminho de Evolução

A interface `ManchesterEngine` abstrai a implementação. Para adicionar ML:

```java
// Atual
@ApplicationScoped
public class ManchesterEngine {
    public CorManchester classificar(TriagemPayloadDTO p) { ... }
}

// Futura versão ML (mesmo contrato)
@Alternative @Priority(10)
@ApplicationScoped
public class ManchesterMLEngine extends ManchesterEngine {
    public CorManchester classificar(TriagemPayloadDTO p) {
        return chamarSageMakerEndpoint(p);
    }
}
```

O CDI do Quarkus seleciona a implementação via `@Alternative` sem alterar o handler.

## Dados para Treino Futuro

O banco nacional já coleta `cor_manchester`, `queixa_principal`, `sinais_vitais` e `tempo_espera_min`. Após 6 meses de operação, haverá dados suficientes para treinar um modelo com revisão médica.
