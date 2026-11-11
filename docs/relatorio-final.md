# EcoGuard — relatório final de validação

Data: 11/11/2026. Ambiente validado: Windows, Java 25.0.2, PostgreSQL 18.6, Maven e JDBC 42.7.8.

## Estado final

O projeto está tecnicamente validado como simulação acadêmica em terminal: login, perfis, CRUD de sensores, leituras, alertas, histórico, encerramento com responsável e consulta de estações. A instalação pelos scripts foi reconstruída e testada em banco isolado. Não há falhas conhecidas nos testes finais.

A compilação e **162 verificações unitárias/simuladas** passaram. O fluxo completo em banco isolado aprovou **178 verificações reais**. São 340 asserções contadas nesses dois programas, além das verificações reais de consulta/autenticação das outras classes; não são 340 cenários independentes.

Todos os dados e estados das cinco sequências do ecoguard principal foram preservados. Nenhuma senha foi alterada, nenhuma ferramenta foi instalada e nenhum commit/push foi realizado. A publicação e a comprovação das contribuições reais do grupo permanecem pendentes.

## Funcionalidades concluídas e verificadas

- Login JDBC com SHA-256 compatível com os dados acadêmicos e identificação do usuário/perfil.
- Permissões dentro do switch para administrador, analista e brigadista.
- CRUD de sensores, incluindo confirmação explícita e cascatas apenas no banco isolado de teste.
- Leituras normais/críticas de temperatura, umidade e fumaça; sensor inativo/inexistente recusado.
- Alerta automático usando o valor efetivamente persistido em NUMERIC(8,2).
- Histórico, relatórios de todos os alertas e filtro de abertos.
- Encerramento com responsável autenticado e preservação do primeiro responsável.
- Consulta de estações e coordenadas.
- Tratamento de entradas inválidas, IDs ausentes, erros de conexão e transações com rollback.
- Abstração, herança, @Override, polimorfismo e composição demonstráveis no código.

## Resultados por execução

| Execução final | Resultado e alcance |
|---|---|
| mvn compile | Aprovado; 15 classes de produção |
| mvn test | 162 verificações aprovadas; quatro classes de teste compiladas |
| BancoConsultaTest | Aprovado no ecoguard existente: seis consultas estruturais, 31 campos, quatro relações sem órfãos, dois filtros de alerta e cinco métodos reais de consulta |
| AutenticacaoJdbcTest | Aprovado: três perfis temporários autenticados, senhas incorretas recusadas, seis casos numéricos e rollback das fixtures |
| FluxoIntegracaoTest | 178 verificações reais aprovadas em banco criado exclusivamente para o teste |
| Proteção do banco principal | Assinaturas de todas as linhas das cinco tabelas e last_value/is_called das cinco sequências idênticos antes/depois |
| Limpeza | Banco isolado criado pela própria execução removido e configuração JVM anterior restaurada |

As execuções finais retornaram BUILD SUCCESS. A primeira execução do fluxo tinha 177 verificações e já passou; a versão final acrescentou a comparação automática do esquema, chegando a 178. Nenhum teste funcional falhou nesta etapa.

## Como o isolamento foi garantido

FluxoIntegracaoTest gera um nome ecoguard_teste_<UUID> e exige o padrão ecoguard_teste_[a-f0-9]{32}. Ele cria esse banco a partir de uma conexão de manutenção em postgres. O nome não é informado pelo usuário e nunca pode ser ecoguard.

ConexaoBanco agora aceita a propriedade JVM ecoguard.db.url, mantendo ecoguard como padrão. Durante o teste, todas as conexões independentes dos DAOs apontam para o banco isolado; a propriedade anterior é restaurada no finally. Não se presume que uma transação externa reverta commits de outras conexões.

Os scripts reais script_banco.sql e dados_iniciais.sql foram executados por psql somente no banco isolado. A senha de demonstração foi gerada aleatoriamente em memória e fornecida ao script apenas como hash; a senha de conexão foi herdada por variável de ambiente, sem ser impressa ou salva em arquivo.

Uma instrumentação de testes, restrita ao banco isolado, usa triggers de statement e sequência para contar tentativas de INSERT/UPDATE/DELETE, inclusive tentativas revertidas ou que afetem zero linhas. Opções negadas e entradas inválidas não incrementaram o contador. Essa instrumentação não foi instalada no ecoguard e não faz parte dos scripts de produção.

O ecoguard principal foi acessado somente para consultas de esquema e assinaturas de verificação. Essas assinaturas ficam em memória; não expõem linhas, nomes, e-mails ou hashes. Após os testes, foi removido exclusivamente o banco que a própria execução criou, com nome validado.

## Cobertura real de escrita e fluxo

O teste executou cadastro/consulta/atualização/exclusão pelos DAOs, leituras normais e críticas dos três tipos, geração de alertas, histórico/relatórios, encerramento e responsável. IDs foram obtidos das consultas após sua geração; não foram presumidos IDs fixos.

O menu App.executar foi percorrido com Scanner de entradas automáticas e todos os DAOs de produção, sem substitutos simulados. O administrador gerenciou sensores, leu dados, registrou leituras e encerrou alerta. O analista registrou/encerrou e foi bloqueado nas alterações de sensores. O brigadista consultou e foi bloqueado nas escritas. Login incorreto não abriu o menu.

Houve verificação de sensor/estação/alerta ausente, sensor inativo, campos vazios, letras em números, tipo/status inválidos, NaN, umidade fora de intervalo e confirmação incorreta de exclusão. Exclusão confirmada e cascatas ocorreram apenas no banco isolado.

Uma restrição adicionada exclusivamente no banco de teste provocou erro ao inserir o alerta; o DAO reverteu também a leitura e não apresentou sucesso. Um responsável sem registro provocou erro de FK no encerramento; o alerta permaneceu aberto e sem responsável. Os DAOs continuaram utilizáveis após essas falhas.

## Esquema e scripts

O banco principal possui 31 campos, cinco PKs, quatro FKs, uma UNIQUE de e-mail, dois CHECKs e seis índices. A reconstrução com os scripts locais reproduziu automaticamente os campos, tipos, tamanhos, precisão/escala, nullabilidade, defaults, identidade, nomes/definições de constraints e índices das cinco tabelas.

Foram preservados SERIAL, coordenadas NUMERIC(9,6), leitura NUMERIC(8,2), nomes/e-mails VARCHAR(100), hash VARCHAR(255), datas opcionais e as regras de cascata observadas. Não foi necessário alterar novamente os scripts nesta etapa. A validação do hash de demonstração ocorre no script psql antes dos INSERTs, pois a tabela real não possui CHECK de formato de hash.

## Arquivos desta etapa

### Criados

| Caminho | Finalidade |
|---|---|
| src/test/java/com/ecoguard/FluxoIntegracaoTest.java | Teste isolado de instalação, esquema, DAOs, menu, permissões, transações e proteção dos dados principais |
| docs/Entrega_Bimestre_1.pdf | Documento original do grupo recuperado do repositório público, sem edição |
| docs/DER_EcoGuard.png | DER original recuperado, sem edição |
| docs/Diagrama_Classe_EcoGuard.png | Diagrama de classes original recuperado, sem edição |

### Modificados

| Caminho | Alteração |
|---|---|
| src/main/java/com/ecoguard/util/ConexaoBanco.java | URL configurável por propriedade JVM apenas no processo; padrão preservado |
| README.md | Resultados reais, comando do novo teste, RFs originais, funcionalidades FI, cronograma/integrantes documentados e repositório correto |
| docs/implementacao.md | Comparação com PDF/diagramas originais e distinção entre planejamento e adaptações implementadas |
| docs/testes.md | Roteiro reproduzível e cobertura dos 178 testes reais |
| docs/revisao-postgresql.md | Complemento final de escrita, instalação e comparação de esquema |
| docs/relatorio-final.md | Este relatório consolidado |

Arquivos gerados em target/auditoria são ignorados pelo Git: validacao-entrega-final.txt, fluxo-integracao-final.txt, consulta-regressao-final.txt, autenticacao-regressao-final.txt e extrações/consultas de apoio. Não são credenciais nem entregáveis de código.

## Preparação do GitHub

O repositório [lorenzzomichelim/EcoGuard_AEP](https://github.com/lorenzzomichelim/EcoGuard_AEP) foi confirmado como público, com branch main. O PDF original aponta para o mesmo endereço, portanto corresponde ao planejamento deste projeto.

A árvore remota ainda contém README, documentos da primeira entrega, scripts SQL de um byte e arquivos de estrutura em src, sem pom.xml ou código Maven desta versão. Nada foi publicado nesta revisão. A pasta local não possui .git, portanto não há remoto local a conferir ou histórico local que possa ser atribuído ao repositório. O executável git não foi encontrado na sessão de ferramentas e nos caminhos padrão verificados; nenhuma instalação foi realizada.

O .gitignore já exclui target, classes, IDEs, arquivos .env, logs e backups. Fontes, pom.xml, scripts e documentação estão organizados para levar ao checkout correto. Não se deve substituir o histórico remoto por um repositório novo nem criar commits para simular contribuições.

A consulta pública de contribuidores, incluindo autores não vinculados a conta, retornou apenas lorenzzomichelim com 20 contribuições. Os nomes e papéis do grupo foram recuperados do planejamento, mas isso não comprova a autoria de cada alteração. O grupo deve registrar/conferir contribuições reais de todos.

## Pendências e limites

1. Associar os arquivos a um checkout do repositório correto usando uma instalação Git já disponível ou uma instalação autorizada pelo usuário. Nenhum .git foi criado automaticamente.
2. Revisar/autorizar commits e push antes da publicação; não foram executados nesta etapa.
3. Comprovar as contribuições reais do grupo e apresentar as adaptações de escopo ao professor. A simulação não implementa coleta IoT contínua, notificações externas, mapas ou análise preditiva. A hierarquia de usuários e a composição previstas no UML foram simplificadas, conforme implementacao.md.

Não há pendência conhecida de compilação, instalação SQL ou dos fluxos funcionais cobertos. Os testes positivos usaram usuários de demonstração no banco isolado; não verificaram senhas de usuários existentes do banco principal. Isso não impede a comprovação do login implementado, mas não permite afirmar que cada credencial histórica foi validada.

## Comandos finais

Configure ECOGUARD_DB_PASSWORD no terminal sem exibir seu valor, conforme README. Para o uso normal:

~~~powershell
$mavenEcoGuard = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd'
& $mavenEcoGuard compile exec:java '-Dexec.mainClass=com.ecoguard.App'
~~~

Para repetir as verificações contadas, incluindo a reconstrução isolada:

~~~powershell
& $mavenEcoGuard '-Decoguard.build.directory=target/entrega-final' compile test exec:java '-Dexec.mainClass=com.ecoguard.FluxoIntegracaoTest' '-Dexec.classpathScope=test'
~~~

Nesta máquina foram usados também -o e -Dmaven.repo.local=C:\Users\loren\.m2\repository, aproveitando o cache existente. A execução isolada precisa de permissão para criar/remover um banco de teste e do psql no caminho registrado. O programa normal usa ecoguard e não cria bancos. A saída do menu é 0.

## Prontidão para a AEP

O EcoGuard está tecnicamente validado para demonstrar Java/POO, CRUD, JDBC, transações e persistência real na simulação de terminal. Os documentos originais foram preservados e comparados; requisitos e cronograma não foram inventados. A aprovação acadêmica depende da aceitação das adaptações e da comprovação de participação do grupo. A publicação aguarda preparação do checkout e autorização para commits/push.
