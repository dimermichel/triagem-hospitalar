#!/bin/bash
# ============================================================
# API Test — ms-triagem
# Testa os endpoints de triagem para ambos os hospitais
# Uso: ./api-test.sh [einstein|hc|all]   (padrão: all)
# ============================================================

EINSTEIN_URL="https://example.com/"  # Substitua pela URL real do Hospital Einstein
HC_URL="https://example2.com/"       # Substitua pela URL real do Hospital das Clínicas

TARGET="${1:-all}"

run_tests() {
    local HOSPITAL="$1"
    local BASE_URL="$2"

    echo ""
    echo "======================================================"
    echo "  Hospital: $HOSPITAL"
    echo "  Base URL: $BASE_URL"
    echo "======================================================"
    echo ""

    # ─── 1. Caso VERMELHO: Dor no peito com irradiação ──────────
    echo "--- [1] Emergência: Dor no peito com irradiação para o braço"
    TRIAGEM_ID=$(curl -s -X POST "$BASE_URL/api/v1/triagem" \
      -H "Content-Type: application/json" \
      -d '{
        "nome": "João da Silva",
        "data_nascimento": "1958-03-15",
        "queixa_principal": "Dor no peito irradiando para o braço esquerdo com suor frio",
        "sinais_vitais": {
          "pressao_sistolica": 190,
          "pressao_diastolica": 110,
          "frequencia_cardiaca": 118,
          "temperatura": 37.1,
          "saturacao_oxigenio": 93,
          "frequencia_respiratoria": 24
        },
        "intensidade_dor": 9,
        "nivel_consciencia": "ALERTA",
        "inicio_subito": true,
        "horas_sintoma": 1,
        "historico_medico": ["hipertensão", "diabetes tipo 2"],
        "medicamentos_uso": ["metformina", "enalapril"]
      }' | python3 -m json.tool | tee /dev/stderr | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('triagem_id', d.get('id', '')))" 2>/dev/null)
    echo ""

    # ─── 2. Caso LARANJA: Falta de ar grave ─────────────────────
    echo "--- [2] Muito urgente: Dispneia com FC elevada"
    curl -s -X POST "$BASE_URL/api/v1/triagem" \
      -H "Content-Type: application/json" \
      -d '{
        "nome": "Maria Oliveira",
        "data_nascimento": "1985-07-20",
        "queixa_principal": "Falta de ar intensa, não consigo respirar direito",
        "sinais_vitais": {
          "pressao_sistolica": 130,
          "pressao_diastolica": 85,
          "frequencia_cardiaca": 138,
          "temperatura": 37.8,
          "saturacao_oxigenio": 88,
          "frequencia_respiratoria": 32
        },
        "intensidade_dor": 6,
        "nivel_consciencia": "ALERTA",
        "inicio_subito": true,
        "horas_sintoma": 2
      }' | python3 -m json.tool
    echo ""

    # ─── 3. Caso AMARELO: Dor abdominal moderada ────────────────
    echo "--- [3] Urgente: Dor abdominal"
    curl -s -X POST "$BASE_URL/api/v1/triagem" \
      -H "Content-Type: application/json" \
      -d '{
        "nome": "Carlos Pereira",
        "data_nascimento": "1990-11-05",
        "queixa_principal": "Dor abdominal no lado direito",
        "sinais_vitais": {
          "pressao_sistolica": 118,
          "pressao_diastolica": 76,
          "frequencia_cardiaca": 88,
          "temperatura": 38.2,
          "saturacao_oxigenio": 98,
          "frequencia_respiratoria": 18
        },
        "intensidade_dor": 6,
        "nivel_consciencia": "ALERTA",
        "inicio_subito": false,
        "horas_sintoma": 8
      }' | python3 -m json.tool
    echo ""

    # ─── 4. Caso VERDE: Dor leve, sinais vitais normais ─────────
    echo "--- [4] Pouco urgente: Torção no tornozelo"
    curl -s -X POST "$BASE_URL/api/v1/triagem" \
      -H "Content-Type: application/json" \
      -d '{
        "nome": "Ana Souza",
        "data_nascimento": "2000-06-10",
        "queixa_principal": "Torção no tornozelo, dor ao caminhar",
        "sinais_vitais": {
          "pressao_sistolica": 115,
          "pressao_diastolica": 72,
          "frequencia_cardiaca": 78,
          "temperatura": 36.7,
          "saturacao_oxigenio": 99,
          "frequencia_respiratoria": 16
        },
        "intensidade_dor": 3,
        "nivel_consciencia": "ALERTA",
        "inicio_subito": false,
        "horas_sintoma": 5
      }' | python3 -m json.tool
    echo ""

    # ─── 5. Caso AZUL: Queixa não urgente ───────────────────────
    echo "--- [5] Não urgente: Resfriado leve"
    curl -s -X POST "$BASE_URL/api/v1/triagem" \
      -H "Content-Type: application/json" \
      -d '{
        "nome": "Pedro Lima",
        "data_nascimento": "1995-02-28",
        "queixa_principal": "Coriza e leve dor de garganta há 2 dias",
        "sinais_vitais": {
          "pressao_sistolica": 118,
          "pressao_diastolica": 74,
          "frequencia_cardiaca": 72,
          "temperatura": 37.2,
          "saturacao_oxigenio": 99,
          "frequencia_respiratoria": 15
        },
        "intensidade_dor": 0,
        "nivel_consciencia": "ALERTA",
        "inicio_subito": false,
        "horas_sintoma": 48
      }' | python3 -m json.tool
    echo ""

    # ─── 6. Consultar status ────────────────────────────────────
    echo "--- [6] Consultar status da triagem [1]"
    TRIAGEM_ID_STATUS="${TRIAGEM_ID:-00000000-0000-0000-0000-000000000000}"
    curl -s "$BASE_URL/api/v1/triagem/$TRIAGEM_ID_STATUS/status" | python3 -m json.tool
    echo ""

    # ─── 7. Iniciar atendimento ──────────────────────────────────
    echo "--- [7] Iniciar atendimento da triagem [1]"
    curl -s -X POST \
      "$BASE_URL/api/v1/triagem/$TRIAGEM_ID_STATUS/iniciar-atendimento?profissionalId=enf-001" \
      -w "HTTP %{http_code}\n"
    echo ""
}

case "$TARGET" in
    einstein)
        run_tests "Hospital Einstein - SP" "$EINSTEIN_URL"
        ;;
    hc)
        run_tests "Hospital das Clínicas - SP" "$HC_URL"
        ;;
    all|*)
        run_tests "Hospital Einstein - SP" "$EINSTEIN_URL"
        run_tests "Hospital das Clínicas - SP" "$HC_URL"
        ;;
esac

echo "======================================================"
echo "  Testes concluídos"
echo "======================================================"
