-- V1__schema_inicial.sql
-- Migration inicial do banco de dados nacional

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS hospitais (
    id              VARCHAR(100) PRIMARY KEY,
    nome            VARCHAR(300) NOT NULL,
    municipio       VARCHAR(200),
    estado          CHAR(2),
    cnes            VARCHAR(20),
    ativo           BOOLEAN DEFAULT TRUE,
    criado_em       TIMESTAMPTZ DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS atendimentos (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    triagem_id                  VARCHAR(100) NOT NULL UNIQUE,
    hospital_id                 VARCHAR(100) NOT NULL REFERENCES hospitais(id),
    data_atendimento            DATE NOT NULL,
    idade                       SMALLINT,
    queixa_principal            TEXT,
    cor_manchester              VARCHAR(10) CHECK (cor_manchester IN ('VERMELHO','LARANJA','AMARELO','VERDE','AZUL')),
    prioridade_ordem            SMALLINT CHECK (prioridade_ordem BETWEEN 1 AND 5),
    tempo_max_espera_min        SMALLINT,
    status                      VARCHAR(30),
    criado_em                   TIMESTAMPTZ,
    classificado_em             TIMESTAMPTZ,
    iniciado_atendimento_em     TIMESTAMPTZ,
    finalizado_em               TIMESTAMPTZ,
    importado_em                TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_atend_hospital_data ON atendimentos (hospital_id, data_atendimento);
CREATE INDEX IF NOT EXISTS idx_atend_cor_data      ON atendimentos (cor_manchester, data_atendimento);
CREATE INDEX IF NOT EXISTS idx_atend_data          ON atendimentos (data_atendimento);

CREATE TABLE IF NOT EXISTS resumos_diarios (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hospital_id             VARCHAR(100) NOT NULL REFERENCES hospitais(id),
    data                    DATE NOT NULL,
    total_atendimentos      INT DEFAULT 0,
    total_vermelho          INT DEFAULT 0,
    total_laranja           INT DEFAULT 0,
    total_amarelo           INT DEFAULT 0,
    total_verde             INT DEFAULT 0,
    total_azul              INT DEFAULT 0,
    tempo_medio_espera_min  NUMERIC(6,2),
    importado_em            TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (hospital_id, data)
);

CREATE OR REPLACE VIEW vw_kpi_mensal AS
SELECT
    hospital_id,
    DATE_TRUNC('month', data_atendimento)   AS mes,
    COUNT(*)                                AS total,
    COUNT(*) FILTER (WHERE cor_manchester = 'VERMELHO') AS vermelho,
    COUNT(*) FILTER (WHERE cor_manchester = 'LARANJA')  AS laranja,
    COUNT(*) FILTER (WHERE cor_manchester = 'AMARELO')  AS amarelo,
    COUNT(*) FILTER (WHERE cor_manchester = 'VERDE')    AS verde,
    COUNT(*) FILTER (WHERE cor_manchester = 'AZUL')     AS azul,
    ROUND(
        AVG(
            CASE WHEN iniciado_atendimento_em IS NOT NULL AND criado_em IS NOT NULL
            THEN EXTRACT(EPOCH FROM (iniciado_atendimento_em - criado_em))::NUMERIC / 60
            END
        ), 1
    ) AS tempo_medio_espera_min,
    ROUND(
        AVG(
            CASE WHEN cor_manchester = 'VERMELHO'
                  AND iniciado_atendimento_em IS NOT NULL AND criado_em IS NOT NULL
            THEN EXTRACT(EPOCH FROM (iniciado_atendimento_em - criado_em))::NUMERIC / 60
            END
        ), 1
    ) AS tempo_medio_espera_vermelho_min
FROM atendimentos
GROUP BY hospital_id, DATE_TRUNC('month', data_atendimento);
