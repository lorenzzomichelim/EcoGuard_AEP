# Testes do EcoGuard

## Resultados confirmados em 11/11/2026

| Execução | Resultado |
|---|---|
| Compilação Maven | Aprovada: 15 classes de produção e quatro de teste |
| AppTest | 162 verificações unitárias/simuladas aprovadas |
| Auditoria SQL no ecoguard real | Cinco tabelas e oito verificações de integridade aprovadas |
| BancoConsultaTest | Consultas, metadados, relacionamentos, relatórios e login inválido aprovados no banco existente |
| AutenticacaoJdbcTest | Três perfis temporários, senhas incorretas e seis casos NUMERIC(8,2) aprovados no PostgreSQL real |
| FluxoIntegracaoTest | 178 verificações reais aprovadas: scripts, esquema, CRUD, leituras, alertas, menu, permissões e rollback |
| Auditoria SQL depois dos testes | Idêntica à inicial; dados agregados e estrutura preservados |

Servidor confirmado: PostgreSQL 18.6. As credenciais configuradas no PowerShell existente do VS Code foram herdadas pelos processos psql/Maven/Java executados nesse terminal. Não foram copiadas para os arquivos de teste.

## Testes locais

~~~powershell
mvn test
~~~

AppTest usa um main executado na fase test pelo exec-maven-plugin. Uma falha lança AssertionError e faz o Maven falhar. Não usa JUnit nem depende de -ea. BancoConsultaTest, AutenticacaoJdbcTest e FluxoIntegracaoTest exigem comandos explícitos e não são disparados por mvn test.

Cobertura: sensores normais/críticos nos limites, polimorfismo, composição e encapsulamento da coleção de leituras, validações de campos/tipos/status/IDs/valores, perfis nas dez opções, bloqueio antes dos DAOs, responsável autenticado, falha de login, falha JDBC, perfil desconhecido, vírgula decimal, cancelamento de exclusão e fim da entrada. Essas verificações locais usam DAOs simulados; são distintas das integrações abaixo.

## Auditoria somente de leitura

~~~powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -W -h localhost -p 5432 -U postgres -d ecoguard -v ON_ERROR_STOP=1 -f database/auditoria_somente_leitura.sql
~~~

-W solicita a senha sem colocá-la no comando. Se PGPASSWORD já estiver configurada no terminal, pode usar -w. O script consulta catálogo, constraints, índices, contagens, regras e relacionamentos em transação READ ONLY com ROLLBACK e timeout. Não exibe nomes, e-mails ou hashes dos usuários. Nenhum resultado dispara correção automática de dados.

A auditoria executada encontrou 31 campos, cinco PKs, quatro FKs, uma UNIQUE, dois CHECKs e seis índices. Os oito totais de inconsistências foram zero. As duas execuções, antes e depois dos testes, retornaram o mesmo resultado: 1 estação, 3 sensores, 6 leituras, 3 alertas abertos sem responsável e 3 usuários.

## JDBC real em tabelas existentes

Configure ECOGUARD_DB_PASSWORD no terminal sem exibir seu valor e execute:

~~~powershell
mvn test-compile exec:java "-Dexec.mainClass=com.ecoguard.BancoConsultaTest" "-Dexec.classpathScope=test"
~~~

O programa verifica seis consultas de estrutura com WHERE FALSE, os metadados de todos os campos e quatro relacionamentos sem órfãos. Executa a consulta de alertas nos dois filtros, lendo IDs, valores, datas e responsável via JDBC. A transação de leitura termina com rollback.

Em conexões próprias, executa também os cinco métodos reais de consulta dos DAOs (sensores, estações, histórico, todos os alertas e abertos), capturando a saída sem publicar dados pessoais. Verifica a recusa de e-mail aleatório inexistente e de uma entrada SQL maliciosa, tratada como parâmetro pelo UsuarioDAO.

Se ECOGUARD_TEST_EMAIL e ECOGUARD_TEST_PASSWORD estiverem configuradas no terminal, verifica também um login válido existente e o perfil retornado. Esse caso **não foi executado** nesta sessão por ausência dessas variáveis. Não tente senhas presumidas nem coloque credenciais nos arquivos.

## Autenticação real com fixtures e rollback

~~~powershell
mvn test-compile exec:java "-Dexec.mainClass=com.ecoguard.AutenticacaoJdbcTest" "-Dexec.classpathScope=test"
~~~

Esse teste conecta ao servidor real e cria duas tabelas exclusivamente em pg_temp, com os tipos/CHECKs copiados das tabelas existentes. Não copia defaults, não utiliza sequências reais e insere IDs explícitos. Não há INSERT/UPDATE/DELETE nas tabelas de public.

Três usuários temporários recebem senhas aleatórias em memória. O PostgreSQL calcula seus hashes; a mesma consulta e implementação SHA-256 do UsuarioDAO deve autenticar administrador, analista e brigadista, devolver ID/perfil corretos e recusar senhas erradas. Usuário inexistente e entrada SQL maliciosa também são recusados. Isso é integração JDBC real com fixtures, sem mocks; não comprova as senhas dos usuários existentes.

Seis leituras temporárias verificam o arredondamento do NUMERIC(8,2), sua conversão por JDBC e o processamento polimórfico do valor persistido. Não executam registrarLeitura nem criam alertas nas tabelas históricas. O rollback é obrigatório e a remoção das duas tabelas temporárias é confirmada com to_regclass.

## Execução com o Maven instalado

~~~powershell
$mavenEcoGuard = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd'
& $mavenEcoGuard '-Decoguard.build.directory=target/revisao-postgresql' test exec:java '-Dexec.mainClass=com.ecoguard.BancoConsultaTest' '-Dexec.classpathScope=test'
& $mavenEcoGuard '-Decoguard.build.directory=target/revisao-postgresql' exec:java '-Dexec.mainClass=com.ecoguard.AutenticacaoJdbcTest' '-Dexec.classpathScope=test'
~~~

Foi usada uma pasta de saída separada para evitar arquivos antigos bloqueados em target. O cache foi indicado por -Dmaven.repo.local=C:\Users\loren\.m2\repository e o modo offline -o foi possível porque as dependências já existiam. Não houve instalação nem configuração global.

## Fluxo completo de escrita em banco isolado — concluído

~~~powershell
mvn test-compile exec:java "-Dexec.mainClass=com.ecoguard.FluxoIntegracaoTest" "-Dexec.classpathScope=test"
~~~

A execução final aprovou **178 verificações reais**. O teste cria um banco ecoguard_teste_<UUID>, executa os dois scripts via psql e compara automaticamente campos, defaults, constraints e índices com o ecoguard principal. Os usuários e a estação/sensores iniciais vêm do script de demonstração; senhas aleatórias ficam apenas em memória e todos os IDs utilizados são consultados após sua geração.

A propriedade ecoguard.db.url aponta todas as conexões dos DAOs para o banco isolado durante o teste. A URL padrão da aplicação não muda, e a propriedade anterior é restaurada ao terminar. O teste verifica o nome do banco antes de suas consultas auxiliares. O nome usado em CREATE/DROP é gerado internamente e precisa passar pela barreira ecoguard_teste_[a-f0-9]{32}; nunca pode ser ecoguard. Ele não utiliza um rollback externo como suposta proteção contra os commits independentes dos DAOs.

Cobertura realizada:

1. Cadastro, consulta, atualização de nome/status e exclusão pelos métodos reais de SensorDAO.
2. Sensor inativo recusado; sensor/estação/alerta inexistentes tratados.
3. Leituras normais e críticas dos três tipos, geração automática de alertas e arredondamento NUMERIC(8,2).
4. Histórico e relatórios reais; encerramento com analista/administrador e ID correto do responsável.
5. Segunda tentativa de encerramento preserva o primeiro responsável; brigadista não encerra.
6. Fluxo App.executar com Scanner de entradas de teste, login PostgreSQL real, todos os DAOs de produção e opções reais do menu. Não usa DAOs simulados.
7. Administrador cadastra, atualiza, registra, consulta, encerra e exclui com confirmação exata. Leituras e alertas desse sensor são removidos em cascata apenas no banco isolado.
8. Analista registra/encerra, mas não altera sensores. Brigadista consulta, mas não registra leituras nem encerra alertas.
9. Triggers de statement e uma sequência apenas no banco isolado contam tentativas de INSERT/UPDATE/DELETE, inclusive comandos revertidos ou que afetem zero linhas. As opções bloqueadas e entradas inválidas não incrementam o contador.
10. Campos vazios, tipo/status incorretos, letras em números, opção inválida, NaN e umidade fora de intervalo são recusados sem escrita.
11. Falha proposital na inserção do alerta reverte a leitura na mesma transação e não imprime sucesso. Falha de FK no responsável reverte o encerramento. Os DAOs continuam funcionando depois dessas falhas.
12. Scripts de instalação e dados iniciais realmente executados no banco novo; campos, constraints e índices iguais aos do principal.

Assinaturas de todas as linhas das cinco tabelas principais e os estados de suas cinco sequências foram comparados antes/depois e ficaram idênticos. Essas assinaturas ficam em memória; não imprimem nomes, e-mails, hashes ou valores de credenciais. O banco isolado foi removido no finally.

O teste contém instrumentação SQL apenas para verificação de permissões e falhas; essa instrumentação não é parte do esquema de produção. Requer permissão PostgreSQL para criar/remover um banco descartável e psql no caminho instalado. Os arquivos de resultado estão em target/auditoria/validacao-entrega-final.txt, consulta-regressao-final.txt e autenticacao-regressao-final.txt.

As 162 verificações locais e as 178 do fluxo isolado somam 340 verificações contadas, além dos testes reais de leitura/autenticação das outras classes. A contagem representa asserções, não 340 cenários independentes. Não há falhas conhecidas nos testes finais.

## Demonstração manual opcional

Para apresentar, execute a aplicação com um usuário autorizado do banco apropriado e percorra o menu. Não exclua sensores nem encerre alertas históricos apenas para demonstração. O login com as senhas existentes do banco principal não foi exercitado automaticamente; os três perfis foram autenticados no banco isolado usando os dados de demonstração.

O relatório [relatorio-final.md](relatorio-final.md) contém comandos finais, inventário e situação de publicação. As diferenças em relação à primeira entrega estão em [implementacao.md](implementacao.md).
