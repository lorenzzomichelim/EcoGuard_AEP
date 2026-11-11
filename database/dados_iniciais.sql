-- Dados ficticios para uma instalacao nova, usando psql.
-- Nao executar no banco ecoguard existente.
-- Informe somente o hash SHA-256 de uma senha ficticia escolhida localmente.
\set ON_ERROR_STOP on
\if :{?senha_hash}
\else
\echo 'Informe o hash de uma senha local com -v senha_hash=... (veja README).'
\quit
\endif
-- A tabela real aceita VARCHAR(255), sem CHECK de hash: validar antes de inserir.
SELECT :'senha_hash' ~ '^[0-9a-f]{64}$' AS hash_valido \gset
\if :hash_valido
\else
\echo 'Hash invalido: forneca 64 caracteres hexadecimais minusculos. Nenhum dado inserido.'
\quit
\endif

BEGIN;
INSERT INTO public.tb_usuario (nome, email, senha_hash, perfil) VALUES
    ('Administrador demonstracao', 'admin@ecoguard.exemplo', :'senha_hash', 'ADMINISTRADOR'),
    ('Analista demonstracao', 'analista@ecoguard.exemplo', :'senha_hash', 'ANALISTA'),
    ('Brigadista demonstracao', 'brigadista@ecoguard.exemplo', :'senha_hash', 'BRIGADISTA');

WITH estacao AS (
    INSERT INTO public.tb_estacao (nome, area_reserva, latitude, longitude)
    VALUES ('Estacao demonstracao', 'Reserva ficticia', -23.420000, -51.930000)
    RETURNING id_estacao
)
INSERT INTO public.tb_sensor (id_estacao, nome, tipo_sensor, status, data_instalacao)
SELECT estacao.id_estacao, tipos.nome, tipos.tipo, 'ATIVO', CURRENT_DATE
FROM estacao CROSS JOIN (VALUES
    ('Temperatura demonstracao', 'TEMPERATURA'),
    ('Umidade demonstracao', 'UMIDADE'),
    ('Fumaca demonstracao', 'FUMACA')
) AS tipos(nome, tipo);
COMMIT;
