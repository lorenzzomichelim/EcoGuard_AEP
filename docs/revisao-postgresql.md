# Validação real PostgreSQL + Java — 11/11/2026

## Resultado

A conexão autenticada foi realizada no **mesmo PowerShell do VS Code configurado pelo usuário**. A variável não era herdada pelos processos isolados do agente. A senha não foi copiada para arquivos nem incluída nos comandos executados.

Servidor confirmado: **PostgreSQL 18.6**, banco **ecoguard**, usuário PostgreSQL **postgres**. Foram executadas duas auditorias somente de leitura, uma exportação apenas do esquema e testes JDBC reais. Os scripts de criação e dados iniciais não foram executados no banco principal; a reconstrução foi posteriormente validada em banco isolado, conforme o complemento final. Não houve escrita em tabelas existentes, alteração de senha, commit Git ou push.

As tentativas bloqueadas por ausência de variável, descritas na revisão anterior, foram superadas usando o terminal existente. Os resultados abaixo substituem a situação anterior.

## Auditoria SQL real

A auditoria usou BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY, timeout local de 15 segundos e ROLLBACK. Foram conferidos 31 campos nas cinco tabelas, tipos completos, nullabilidade, defaults, geração de IDs, constraints e índices. A exportação pg_dump foi somente de esquema, sem dados, proprietários ou permissões.

| Tabela | Registros antes | Registros depois |
|---|---:|---:|
| TB_ESTACAO | 1 | 1 |
| TB_SENSOR | 3 | 3 |
| TB_LEITURA | 6 | 6 |
| TB_ALERTA | 3 | 3 |
| TB_USUARIO | 3 | 3 |

As oito verificações retornaram **zero inconsistências**, antes e depois: sensores sem estação, leituras sem sensor, alertas sem leitura, responsáveis inexistentes, tipo/status de sensor inválidos, status/criticidade de alerta inválidos, perfil/formato de hash inválidos e leituras fora das regras Java. Há seis leituras associadas a sensores e três alertas ABERTO sem responsável. Os arquivos completos das duas auditorias foram comparados e são idênticos. Essa comparação cobre o catálogo e os resultados agregados, não é um checksum individual de todas as linhas.

Foram confirmadas cinco PKs, quatro FKs, uma UNIQUE de e-mail, dois CHECKs e seis índices. As FKs são estação–sensor, sensor–leitura, leitura–alerta e usuário–alerta. Há ON DELETE CASCADE em leitura→sensor e alerta→leitura. O responsável é opcional e o LEFT JOIN funciona com os três alertas atuais sem responsável.

## Diferenças encontradas e corrigidas nos scripts locais

| Item | Referência anterior | Estrutura real incorporada ao script |
|---|---|---|
| IDs das cinco tabelas | INTEGER IDENTITY | SERIAL, com sequência e default nextval |
| Nomes | VARCHAR(150) | VARCHAR(100) |
| Reserva da estação | VARCHAR(200), opcional | VARCHAR(150), obrigatória |
| Coordenadas | NUMERIC(10,7) | NUMERIC(9,6) |
| Valor da leitura | DOUBLE PRECISION | NUMERIC(8,2) |
| E-mail | VARCHAR(200) | VARCHAR(100), UNIQUE |
| Hash | VARCHAR(64) com CHECK | VARCHAR(255), obrigatório, sem CHECK de formato |
| Status de sensor/alerta e criticidade | VARCHAR(10) em vários campos | VARCHAR(20) |
| Datas de eventos | NOT NULL em vários campos | Opcionais, com CURRENT_TIMESTAMP |
| Instalação do sensor | Default CURRENT_DATE | Opcional, sem default; o DAO fornece CURRENT_DATE |
| Unidade da leitura | NOT NULL | Opcional |
| Status inicial de alerta | Default ABERTO | Sem default; o DAO fornece ABERTO |
| CHECKs | Restrições adicionais não confirmadas | Apenas tipo de sensor e perfil, com nomes reais |

O script database/script_banco.sql agora acompanha o catálogo observado, inclusive os nomes das FKs e CHECKs. SERIAL é a abreviação PostgreSQL que cria a sequência e seu default, sem copiar o valor atual das sequências existentes. Não foi aplicada qualquer migração ao banco.

O script dados_iniciais.sql foi ajustado para validar o hash na própria execução psql antes dos INSERTs, pois a tabela real não contém o CHECK anteriormente presumido. Ele continua exclusivo para instalação nova e foi posteriormente executado apenas no banco isolado do complemento final. Os scripts de instalação foram inicialmente comparados com o catálogo e a exportação de esquema; sua execução em banco novo foi depois aprovada no complemento final deste documento.

## Correção de comportamento Java

NUMERIC(8,2) arredonda os valores. Antes, por exemplo, uma entrada de temperatura 54,999 era persistida como 55,00, mas o alerta era decidido usando 54,999. Isso podia produzir uma leitura aparentemente crítica no histórico sem o alerta correspondente.

LeituraDAO agora usa INSERT ... RETURNING id_leitura, valor e adiciona ao sensor o valor efetivamente retornado pelo banco antes de chamar processarLeitura(). A decisão fica coerente com a leitura persistida. A validação de entrada continua ocorrendo antes do INSERT.

UsuarioDAO recebeu uma sobrecarga que aceita Connection. O método original continua abrindo/fechando sua conexão e delega à mesma consulta. Isso permite executar autenticação real com fixtures temporárias numa transação controlada pelo teste, sem mudar credenciais existentes.

## Testes JDBC realmente executados

### BancoConsultaTest — tabelas existentes

Aprovado, pelo Maven, usando JDBC 42.7.8 e Java 25:

- Conexão autenticada ao ecoguard.
- Seis consultas de estrutura, incluindo todas as colunas e JOIN de alertas.
- Metadados dos 31 campos com tipos, tamanhos, precisão, escala, nullabilidade, defaults e identidade.
- Quatro verificações de integridade referencial, sem registros órfãos.
- Consulta parametrizada de todos os alertas: três registros.
- Consulta parametrizada apenas de abertos: três registros.
- Recusa de e-mail inexistente e de entrada SQL maliciosa tratada como parâmetro, pelo UsuarioDAO real.
- Cinco consultas pelos DAOs de produção: sensores, estações, histórico, todos os alertas e alertas abertos. A saída foi capturada em memória para não publicar dados pessoais; nenhuma apresentou erro.

As consultas principais foram executadas em transação somente de leitura, encerrada com rollback. Os DAOs de consulta e autenticação abriram suas conexões próprias e executaram somente SELECTs.

O login válido com **credenciais de usuários existentes** não foi executado: ECOGUARD_TEST_EMAIL/ECOGUARD_TEST_PASSWORD não estavam configuradas. Não foram tentadas senhas presumidas. Esse caso é diferente do teste positivo abaixo.

### AutenticacaoJdbcTest — fixtures reais temporárias

Aprovado no mesmo PostgreSQL, **sem mocks**:

- Tabelas em pg_temp criadas com LIKE das tabelas reais, preservando tipos e CHECKs.
- Defaults não copiados e IDs explícitos: nenhuma sequência das tabelas existentes foi utilizada.
- Três usuários fictícios temporários, um por perfil, com senhas aleatórias geradas em memória.
- Hash de fixture calculado pelo PostgreSQL e comparado ao SHA-256 do UsuarioDAO Java, usando a mesma consulta de produção.
- Três logins válidos aprovados com ID/perfil corretos; três senhas incorretas, usuário inexistente e entrada SQL maliciosa recusados.
- Seis INSERTs em leitura temporária para verificar arredondamento NUMERIC(8,2), retorno por getDouble e processamento polimórfico: 54,999→55; 20,004→20; 149,999→150; 54,994→54,99; 20,006→20,01; 149,994→149,99. Os três primeiros são críticos e os demais normais.
- ROLLBACK executado e ausência das duas tabelas temporárias confirmada por to_regclass.

Esse teste comprova autenticação e conversão numérica reais com fixtures. Não equivale a autenticar as senhas dos usuários existentes nem a executar o fluxo completo registrarLeitura com criação de alertas nas tabelas históricas.

### Compilação e testes locais

Na revisão inicial, Maven recompilou 15 classes de produção e três de teste. A validação final possui quatro classes de teste. As **162 verificações unitárias/simuladas** também passaram, separadamente dos testes JDBC. Todas as execuções Maven finais retornaram BUILD SUCCESS.

## Comandos reproduzíveis

Execute no terminal que já possui ECOGUARD_DB_PASSWORD; para psql, PGPASSWORD deve estar configurada nesse terminal ou use -W para solicitação interativa. Nunca exiba os valores.

~~~powershell
$mavenEcoGuard = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd'
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -w -h 127.0.0.1 -p 5432 -U postgres -d ecoguard -v ON_ERROR_STOP=1 -f database/auditoria_somente_leitura.sql
& $mavenEcoGuard '-Decoguard.build.directory=target/revisao-postgresql' test exec:java '-Dexec.mainClass=com.ecoguard.BancoConsultaTest' '-Dexec.classpathScope=test'
& $mavenEcoGuard '-Decoguard.build.directory=target/revisao-postgresql' exec:java '-Dexec.mainClass=com.ecoguard.AutenticacaoJdbcTest' '-Dexec.classpathScope=test'
~~~

Nesta máquina foi usado também -o e -Dmaven.repo.local=C:\Users\loren\.m2\repository, apontando para o cache existente. Nenhuma ferramenta ou biblioteca foi instalada.

## Evidências locais e limites

Os arquivos abaixo estão em target/auditoria, ignorados pelo Git:

- postgresql-real.txt: primeira auditoria SQL, concluída com ROLLBACK.
- postgresql-real-final.txt: auditoria após os testes, idêntica à primeira.
- esquema-real.sql: exportação somente do esquema, sem dados.
- jdbc-real.txt: compilação, 162 verificações locais e BancoConsultaTest aprovados.
- autenticacao-jdbc-real.txt: fixtures de autenticação e arredondamento, com rollback confirmado.

## Complemento final: escrita e reconstrução aprovadas

A pendência de escrita da revisão inicial foi concluída em 11/11/2026. FluxoIntegracaoTest criou um banco com nome aleatório ecoguard_teste_<UUID>, executou script_banco.sql e dados_iniciais.sql via psql e comparou automaticamente os campos, defaults, constraints e índices das cinco tabelas com o banco principal. A comparação foi aprovada.

O teste usa a propriedade JVM ecoguard.db.url para que todas as conexões independentes dos DAOs apontem para o banco isolado; o padrão do aplicativo permanece ecoguard. Não se presume que rollback em uma conexão externa desfaça commits em outras conexões.

Foram aprovadas **178 verificações reais**, cobrindo cadastro, consulta, atualização, exclusão/cascatas de sensor; leituras normais e críticas dos três tipos, alertas e arredondamento; histórico/relatórios; encerramento com responsável e preservação do primeiro responsável; menu com os três perfis; IDs ausentes e entradas inválidas.

Um contador de tentativas de DML foi instalado somente no banco de teste com triggers de statement e uma sequência. Ele detecta até tentativas revertidas por rollback ou comandos que afetem zero linhas. Nenhuma operação proibida dos perfis incrementou esse contador.

A falha intencional na inserção de alerta confirmou rollback da leitura associada e ausência de mensagem de sucesso. Uma FK de responsável inválido confirmou que o encerramento falho preserva o alerta aberto. Depois dessas falhas, os DAOs continuaram operacionais.

Antes/depois, foram comparadas em memória assinaturas de todas as linhas das cinco tabelas principais e os estados last_value/is_called de suas cinco sequências. Ficaram idênticos. O banco isolado foi removido no finally e a propriedade de conexão foi restaurada. Somente o banco criado pela própria execução, cujo nome precisa passar pela validação de prefixo/UUID, pode ser removido por esse teste.

Evidências adicionais em target/auditoria:

- validacao-entrega-final.txt: compile, 162 verificações locais e 178 reais, BUILD SUCCESS.
- fluxo-integracao-final.txt: primeira execução aprovada, com 177 verificações; a execução final acrescentou a comparação automática de esquema.
- consulta-regressao-final.txt: consultas reais aprovadas com o build final.
- autenticacao-regressao-final.txt: autenticação temporária e conversões aprovadas com o build final.

O fluxo completo foi exercitado com usuários do script de demonstração no banco isolado. As credenciais dos usuários do banco principal não foram testadas nem alteradas; não são necessárias para comprovar o login funcional com os usuários de demonstração. Os três alertas originais permaneceram abertos e todos os dados existentes foram preservados.

~~~powershell
mvn test-compile exec:java "-Dexec.mainClass=com.ecoguard.FluxoIntegracaoTest" "-Dexec.classpathScope=test"
~~~

A execução requer permissão PostgreSQL para criar/remover o banco descartável e o psql instalado no caminho Windows registrado no teste. Não instala ferramentas e não modifica configurações globais. Veja [relatorio-final.md](relatorio-final.md) para o inventário e as pendências de publicação.
