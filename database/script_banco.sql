-- PostgreSQL 18.6: estrutura conferida no catalogo real em 11/11/2026.
-- Executar SOMENTE em um banco novo e vazio. Nao executar no ecoguard existente.
-- Nenhum dado ou valor atual de sequencia foi exportado.
BEGIN;

CREATE TABLE public.tb_estacao (
    id_estacao SERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    area_reserva VARCHAR(150) NOT NULL,
    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.tb_sensor (
    id_sensor SERIAL PRIMARY KEY,
    id_estacao INTEGER NOT NULL,
    nome VARCHAR(100) NOT NULL,
    tipo_sensor VARCHAR(20) NOT NULL,
    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),
    status VARCHAR(20) NOT NULL,
    data_instalacao DATE,
    CONSTRAINT ck_sensor_tipo CHECK (tipo_sensor IN ('TEMPERATURA', 'UMIDADE', 'FUMACA')),
    CONSTRAINT fk_sensor_estacao FOREIGN KEY (id_estacao) REFERENCES public.tb_estacao(id_estacao)
);

CREATE TABLE public.tb_usuario (
    id_usuario SERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    data_cadastro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('ADMINISTRADOR', 'ANALISTA', 'BRIGADISTA'))
);

CREATE TABLE public.tb_leitura (
    id_leitura SERIAL PRIMARY KEY,
    id_sensor INTEGER NOT NULL,
    data_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    valor NUMERIC(8,2) NOT NULL,
    unidade_medida VARCHAR(10),
    CONSTRAINT fk_leitura_sensor FOREIGN KEY (id_sensor) REFERENCES public.tb_sensor(id_sensor) ON DELETE CASCADE
);

CREATE TABLE public.tb_alerta (
    id_alerta SERIAL PRIMARY KEY,
    id_leitura INTEGER NOT NULL,
    id_usuario_resp INTEGER,
    data_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    nivel_criticidade VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_alerta_leitura FOREIGN KEY (id_leitura) REFERENCES public.tb_leitura(id_leitura) ON DELETE CASCADE,
    CONSTRAINT fk_alerta_usuario FOREIGN KEY (id_usuario_resp) REFERENCES public.tb_usuario(id_usuario)
);

COMMIT;
