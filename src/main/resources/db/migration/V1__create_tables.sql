-- V1__create_tables.sql
-- Creates all five core tables for the Precatorios API

CREATE TABLE processos (
    id              BIGSERIAL PRIMARY KEY,
    numero          VARCHAR(25) NOT NULL UNIQUE,
    classe          VARCHAR(200),
    assunto         VARCHAR(500),
    foro            VARCHAR(200),
    vara            VARCHAR(200),
    juiz            VARCHAR(300),
    valor_acao      VARCHAR(50),
    status          VARCHAR(50) DEFAULT 'PENDENTE',
    data_coleta     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    dados_brutos    JSONB
);

CREATE TABLE credores (
    id                  BIGSERIAL PRIMARY KEY,
    nome                VARCHAR(500) NOT NULL,
    cpf_cnpj            VARCHAR(20),
    advogado            VARCHAR(500),
    processo_id         BIGINT REFERENCES processos(id),
    tipo_participacao   VARCHAR(50),
    data_descoberta     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(nome, processo_id)
);

CREATE TABLE precatorios (
    id                      BIGSERIAL PRIMARY KEY,
    numero_precatorio       VARCHAR(30),
    numero_processo         VARCHAR(25),
    credor_id               BIGINT REFERENCES credores(id),
    entidade_devedora       VARCHAR(300),
    valor_original          DECIMAL(15,2),
    valor_atualizado        DECIMAL(15,2),
    natureza                VARCHAR(20),
    status_pagamento        VARCHAR(30),
    posicao_cronologica     INTEGER,
    data_expedicao          DATE,
    data_coleta             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    dados_brutos            JSONB
);

CREATE TABLE prospeccoes (
    id                      BIGSERIAL PRIMARY KEY,
    processo_semente        VARCHAR(25) NOT NULL,
    status                  VARCHAR(20) DEFAULT 'EM_ANDAMENTO',
    profundidade_max        INTEGER DEFAULT 2,
    max_credores            INTEGER DEFAULT 50,
    credores_encontrados    INTEGER DEFAULT 0,
    processos_visitados     INTEGER DEFAULT 0,
    data_inicio             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_fim                TIMESTAMP,
    erro_mensagem           TEXT
);

CREATE TABLE leads (
    id                  BIGSERIAL PRIMARY KEY,
    prospeccao_id       BIGINT REFERENCES prospeccoes(id),
    credor_id           BIGINT REFERENCES credores(id),
    precatorio_id       BIGINT REFERENCES precatorios(id),
    score               INTEGER DEFAULT 0,
    score_detalhes      JSONB,
    status_contato      VARCHAR(30) DEFAULT 'NAO_CONTACTADO',
    data_criacao        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Performance indexes
CREATE INDEX idx_processos_numero ON processos(numero);
CREATE INDEX idx_credores_processo_id ON credores(processo_id);
CREATE INDEX idx_precatorios_credor_id ON precatorios(credor_id);
CREATE INDEX idx_leads_prospeccao_id ON leads(prospeccao_id);
CREATE INDEX idx_leads_score ON leads(score DESC);
CREATE INDEX idx_prospeccoes_status ON prospeccoes(status);
