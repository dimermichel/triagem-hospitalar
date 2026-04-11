package br.gov.sus.triagem.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Testes de integração do TriagemResource.
 * Roda com Quarkus Dev Services (LocalStack automático para SQS/DynamoDB).
 */
@QuarkusTest
class TriagemResourceIT {

    private static final String BASE = "/api/v1/triagem";

    @Test
    @DisplayName("POST /triagem com payload válido → 202 Accepted")
    void postTriagemValido() {
        given()
            .contentType(ContentType.JSON)
            .body(payloadValido("dor no peito", 7))
        .when()
            .post(BASE)
        .then()
            .statusCode(202)
            .body("triagem_id", notNullValue())
            .body("status", equalTo("AGUARDANDO_CLASSIFICACAO"))
            .body("hospital_id", notNullValue());
    }

    @Test
    @DisplayName("POST /triagem sem nome → 400 Bad Request")
    void postTriagemSemNome() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "data_nascimento": "1990-01-01",
                  "queixa_principal": "Dor de cabeça",
                  "sinais_vitais": {
                    "pressao_sistolica": 120, "pressao_diastolica": 80,
                    "frequencia_cardiaca": 75, "temperatura": 36.8,
                    "saturacao_oxigenio": 98
                  },
                  "intensidade_dor": 4,
                  "nivel_consciencia": "ALERTA"
                }
                """)
        .when()
            .post(BASE)
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("POST /triagem com nível de consciência inválido → 400")
    void postTriagemNivelInvalido() {
        given()
            .contentType(ContentType.JSON)
            .body(payloadComNivel("DORMINDO"))
        .when()
            .post(BASE)
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("POST /triagem com dor fora do range (11) → 400")
    void postTriagemDorInvalida() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "nome": "Teste",
                  "data_nascimento": "1990-01-01",
                  "queixa_principal": "Cefaleia",
                  "sinais_vitais": {
                    "pressao_sistolica": 120, "pressao_diastolica": 80,
                    "frequencia_cardiaca": 75, "temperatura": 36.8,
                    "saturacao_oxigenio": 98
                  },
                  "intensidade_dor": 11,
                  "nivel_consciencia": "ALERTA"
                }
                """)
        .when()
            .post(BASE)
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("GET /triagem/{id}/status com ID inexistente → 404")
    void getStatusInexistente() {
        given()
        .when()
            .get(BASE + "/00000000-0000-0000-0000-000000000000/status")
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("POST + GET status — fluxo completo")
    void fluxoCompletoCriarEConsultar() {
        String triagemId =
            given()
                .contentType(ContentType.JSON)
                .body(payloadValido("febre alta", 3))
            .when()
                .post(BASE)
            .then()
                .statusCode(202)
                .extract().path("triagem_id");

        given()
        .when()
            .get(BASE + "/" + triagemId + "/status")
        .then()
            .statusCode(200)
            .body("triagem_id", equalTo(triagemId));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String payloadValido(String queixa, int dor) {
        return """
            {
              "nome": "Paciente Teste",
              "data_nascimento": "1985-06-15",
              "queixa_principal": "%s",
              "sinais_vitais": {
                "pressao_sistolica": 125,
                "pressao_diastolica": 82,
                "frequencia_cardiaca": 88,
                "temperatura": 38.2,
                "saturacao_oxigenio": 97,
                "frequencia_respiratoria": 18
              },
              "intensidade_dor": %d,
              "nivel_consciencia": "ALERTA",
              "inicio_subito": false,
              "horas_sintoma": 6,
              "historico_medico": ["hipertensão"],
              "gestante": false
            }
            """.formatted(queixa, dor);
    }

    private String payloadComNivel(String nivel) {
        return """
            {
              "nome": "Paciente Teste",
              "data_nascimento": "1990-01-01",
              "queixa_principal": "Teste",
              "sinais_vitais": {
                "pressao_sistolica": 120, "pressao_diastolica": 80,
                "frequencia_cardiaca": 75, "temperatura": 36.8,
                "saturacao_oxigenio": 98
              },
              "intensidade_dor": 3,
              "nivel_consciencia": "%s"
            }
            """.formatted(nivel);
    }
}
