-- ============================================================
-- Schema do banco de dados nacional — Aurora PostgreSQL
-- Recebe os consolidados diários de todos os hospitais
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── Hospitais cadastrados ────────────────────────────────────
CREATE TABLE IF NOT EXISTS hospitais (
    id              VARCHAR(100) PRIMARY KEY,
    nome            VARCHAR(300) NOT NULL,
    municipio       VARCHAR(200),
    estado          CHAR(2),
    regiao          VARCHAR(100),
    cnes            VARCHAR(20),
    ativo           BOOLEAN DEFAULT TRUE,
    criado_em       TIMESTAMPTZ DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ DEFAULT NOW()
);

-- ── Atendimentos individuais ─────────────────────────────────
CREATE TABLE IF NOT EXISTS atendimentos (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    triagem_id              VARCHAR(100) NOT NULL,
    hospital_id             VARCHAR(100) NOT NULL REFERENCES hospitais(id),
    data_atendimento        DATE NOT NULL,

    -- Dados do paciente (sem PII sensível nesta versão do MVP)
    idade                   SMALLINT,
    queixa_principal        TEXT,

    -- Classificação Manchester
    cor_manchester          VARCHAR(10) CHECK (cor_manchester IN ('VERMELHO','LARANJA','AMARELO','VERDE','AZUL')),
    prioridade_ordem        SMALLINT CHECK (prioridade_ordem BETWEEN 1 AND 5),
    tempo_max_espera_min    SMALLINT,

    -- Status final
    status                  VARCHAR(30),

    -- Tempos (para KPIs)
    criado_em               TIMESTAMPTZ,
    classificado_em         TIMESTAMPTZ,
    iniciado_atendimento_em TIMESTAMPTZ,
    finalizado_em           TIMESTAMPTZ,

    -- Tempos calculados (minutos)
    tempo_espera_min        INT GENERATED ALWAYS AS (
        CASE
            WHEN iniciado_atendimento_em IS NOT NULL AND criado_em IS NOT NULL
            THEN EXTRACT(EPOCH FROM (iniciado_atendimento_em - criado_em))::INT / 60
        END
    ) STORED,

    tempo_atendimento_min   INT GENERATED ALWAYS AS (
        CASE
            WHEN finalizado_em IS NOT NULL AND iniciado_atendimento_em IS NOT NULL
            THEN EXTRACT(EPOCH FROM (finalizado_em - iniciado_atendimento_em))::INT / 60
        END
    ) STORED,

    importado_em            TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_atendimentos_hospital_data
    ON atendimentos (hospital_id, data_atendimento);

CREATE INDEX IF NOT EXISTS idx_atendimentos_cor
    ON atendimentos (cor_manchester, data_atendimento);

CREATE INDEX IF NOT EXISTS idx_atendimentos_data
    ON atendimentos (data_atendimento);

-- ── Resumos diários pré-calculados ──────────────────────────
CREATE TABLE IF NOT EXISTS resumos_diarios (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hospital_id         VARCHAR(100) NOT NULL REFERENCES hospitais(id),
    data                DATE NOT NULL,
    total_atendimentos  INT DEFAULT 0,
    total_vermelho      INT DEFAULT 0,
    total_laranja       INT DEFAULT 0,
    total_amarelo       INT DEFAULT 0,
    total_verde         INT DEFAULT 0,
    total_azul          INT DEFAULT 0,
    tempo_medio_espera_min NUMERIC(6,2),
    importado_em        TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE (hospital_id, data)
);

-- ── View: KPI por hospital e mês ─────────────────────────────
CREATE OR REPLACE VIEW vw_kpi_mensal AS
SELECT
    hospital_id,
    DATE_TRUNC('month', data_atendimento) AS mes,
    COUNT(*)                              AS total,
    COUNT(*) FILTER (WHERE cor_manchester = 'VERMELHO') AS vermelho,
    COUNT(*) FILTER (WHERE cor_manchester = 'LARANJA')  AS laranja,
    COUNT(*) FILTER (WHERE cor_manchester = 'AMARELO')  AS amarelo,
    COUNT(*) FILTER (WHERE cor_manchester = 'VERDE')    AS verde,
    COUNT(*) FILTER (WHERE cor_manchester = 'AZUL')     AS azul,
    ROUND(AVG(tempo_espera_min), 1)       AS tempo_medio_espera_min,
    ROUND(AVG(CASE WHEN cor_manchester = 'VERMELHO' THEN tempo_espera_min END), 1)
                                          AS tempo_medio_espera_vermelho_min
FROM atendimentos
GROUP BY hospital_id, DATE_TRUNC('month', data_atendimento);

-- ── Trigger: atualiza resumo_diário ao inserir atendimento ───
CREATE OR REPLACE FUNCTION fn_atualiza_resumo_diario()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO resumos_diarios (hospital_id, data, total_atendimentos,
        total_vermelho, total_laranja, total_amarelo, total_verde, total_azul)
    VALUES (NEW.hospital_id, NEW.data_atendimento, 1,
        (NEW.cor_manchester = 'VERMELHO')::INT,
        (NEW.cor_manchester = 'LARANJA')::INT,
        (NEW.cor_manchester = 'AMARELO')::INT,
        (NEW.cor_manchester = 'VERDE')::INT,
        (NEW.cor_manchester = 'AZUL')::INT)
    ON CONFLICT (hospital_id, data) DO UPDATE SET
        total_atendimentos = resumos_diarios.total_atendimentos + 1,
        total_vermelho  = resumos_diarios.total_vermelho  + (NEW.cor_manchester = 'VERMELHO')::INT,
        total_laranja   = resumos_diarios.total_laranja   + (NEW.cor_manchester = 'LARANJA')::INT,
        total_amarelo   = resumos_diarios.total_amarelo   + (NEW.cor_manchester = 'AMARELO')::INT,
        total_verde     = resumos_diarios.total_verde     + (NEW.cor_manchester = 'VERDE')::INT,
        total_azul      = resumos_diarios.total_azul      + (NEW.cor_manchester = 'AZUL')::INT;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_atualiza_resumo_diario
AFTER INSERT ON atendimentos
FOR EACH ROW EXECUTE FUNCTION fn_atualiza_resumo_diario();
