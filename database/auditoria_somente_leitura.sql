-- psql: executar com -X -v ON_ERROR_STOP=1 -f database/auditoria_somente_leitura.sql
-- Somente catalogo, contagens e verificacoes; sem dados pessoais ou hashes na saida.
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '15s';

SELECT current_database() AS banco, current_user AS usuario,
       current_setting('server_version') AS versao;

-- Nomes, tipos completos, obrigatoriedade, valores padrao e geracao de IDs.
SELECT c.relname AS tabela, a.attname AS coluna,
       pg_catalog.format_type(a.atttypid, a.atttypmod) AS tipo,
       a.attnotnull AS obrigatorio,
       pg_get_expr(d.adbin, d.adrelid) AS valor_padrao,
       a.attidentity AS identidade
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid
LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
WHERE n.nspname = 'public'
  AND c.relname IN ('tb_estacao', 'tb_sensor', 'tb_leitura', 'tb_alerta', 'tb_usuario')
  AND a.attnum > 0 AND NOT a.attisdropped
ORDER BY c.relname, a.attnum;

-- PK, FK (incluindo regras de exclusao), UNIQUE e CHECK.
SELECT c.relname AS tabela, k.conname AS restricao, k.contype AS tipo,
       pg_get_constraintdef(k.oid) AS definicao, k.convalidated AS validada
FROM pg_catalog.pg_constraint k
JOIN pg_catalog.pg_class c ON c.oid = k.conrelid
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relname IN ('tb_estacao', 'tb_sensor', 'tb_leitura', 'tb_alerta', 'tb_usuario')
ORDER BY c.relname, k.conname;

SELECT tablename AS tabela, indexname AS indice, indexdef AS definicao
FROM pg_catalog.pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('tb_estacao', 'tb_sensor', 'tb_leitura', 'tb_alerta', 'tb_usuario')
ORDER BY tablename, indexname;

SELECT 'tb_estacao' AS tabela, count(*) AS registros FROM TB_ESTACAO
UNION ALL SELECT 'tb_sensor', count(*) FROM TB_SENSOR
UNION ALL SELECT 'tb_leitura', count(*) FROM TB_LEITURA
UNION ALL SELECT 'tb_alerta', count(*) FROM TB_ALERTA
UNION ALL SELECT 'tb_usuario', count(*) FROM TB_USUARIO;

-- Todos os resultados abaixo devem ser zero; nenhuma correcao automatica.
SELECT 'sensores_sem_estacao' AS verificacao, count(*) AS inconsistencias
FROM TB_SENSOR s LEFT JOIN TB_ESTACAO e ON e.id_estacao = s.id_estacao
WHERE e.id_estacao IS NULL
UNION ALL
SELECT 'leituras_sem_sensor', count(*)
FROM TB_LEITURA l LEFT JOIN TB_SENSOR s ON s.id_sensor = l.id_sensor
WHERE s.id_sensor IS NULL
UNION ALL
SELECT 'alertas_sem_leitura', count(*)
FROM TB_ALERTA a LEFT JOIN TB_LEITURA l ON l.id_leitura = a.id_leitura
WHERE l.id_leitura IS NULL
UNION ALL
SELECT 'responsaveis_inexistentes', count(*)
FROM TB_ALERTA a LEFT JOIN TB_USUARIO u ON u.id_usuario = a.id_usuario_resp
WHERE a.id_usuario_resp IS NOT NULL AND u.id_usuario IS NULL
UNION ALL
SELECT 'tipos_ou_status_sensor_invalidos', count(*) FROM TB_SENSOR
WHERE tipo_sensor IS NULL OR tipo_sensor NOT IN ('TEMPERATURA', 'UMIDADE', 'FUMACA')
   OR status IS NULL OR status NOT IN ('ATIVO', 'INATIVO')
UNION ALL
SELECT 'status_ou_nivel_alerta_invalidos', count(*) FROM TB_ALERTA
WHERE status IS NULL OR status NOT IN ('ABERTO', 'ENCERRADO')
   OR nivel_criticidade IS NULL OR nivel_criticidade NOT IN ('ALERTA', 'CRITICO')
UNION ALL
SELECT 'perfil_ou_formato_hash_invalido', count(*) FROM TB_USUARIO
WHERE perfil IS NULL OR perfil NOT IN ('ADMINISTRADOR', 'ANALISTA', 'BRIGADISTA')
   OR senha_hash IS NULL OR btrim(senha_hash) !~ '^[0-9a-f]{64}$'
UNION ALL
SELECT 'leituras_fora_das_regras_java', count(*)
FROM TB_LEITURA l JOIN TB_SENSOR s ON s.id_sensor = l.id_sensor
WHERE l.valor IS NULL
   OR NOT (l.valor > '-Infinity'::DOUBLE PRECISION AND l.valor < 'Infinity'::DOUBLE PRECISION)
   OR (s.tipo_sensor = 'UMIDADE' AND (l.valor < 0 OR l.valor > 100))
   OR (s.tipo_sensor = 'FUMACA' AND l.valor < 0);

-- Contagens dos relacionamentos utilizados nos relatorios Java.
SELECT count(*) AS leituras_com_sensor
FROM TB_LEITURA l JOIN TB_SENSOR s ON s.id_sensor = l.id_sensor;
SELECT a.status, count(*) AS alertas, count(u.id_usuario) AS com_responsavel
FROM TB_ALERTA a
JOIN TB_LEITURA l ON l.id_leitura = a.id_leitura
JOIN TB_SENSOR s ON s.id_sensor = l.id_sensor
LEFT JOIN TB_USUARIO u ON u.id_usuario = a.id_usuario_resp
GROUP BY a.status ORDER BY a.status;

ROLLBACK;
