# EcoGuard

Projeto acadêmico da AEP do 4º semestre de Engenharia de Software da UniCesumar. O objetivo é simular o monitoramento ambiental de reservas e identificar leituras que podem indicar risco de incêndios, em alinhamento ao **ODS 15 — Vida Terrestre**.

As leituras são digitadas no terminal. Não existem sensores físicos conectados e os limites não constituem parâmetros científicos ou de segurança.

## Tecnologias e organização

Java 25, PostgreSQL 18.6, JDBC e Maven. O repositório público previsto foi confirmado: [lorenzzomichelim/EcoGuard_AEP](https://github.com/lorenzzomichelim/EcoGuard_AEP). Esta pasta local não contém .git nem está vinculada a um remoto; nenhum commit ou push foi realizado nesta revisão.

- src/main/java/com/ecoguard/App.java: login, menu e permissões.
- model/: classe abstrata de sensor, suas três subclasses e usuário autenticado, com composição de leituras.
- dao/: operações JDBC para sensores, leituras, relatórios, usuários, alertas e estações.
- util/: conexão e validações comuns.
- src/test/: testes sem bibliotecas adicionais.
- database/: scripts de instalação validados em banco isolado.
- docs/: implementação, testes e pendências acadêmicas.

## Requisitos funcionais e implementação

A primeira entrega original está em [docs/Entrega_Bimestre_1.pdf](docs/Entrega_Bimestre_1.pdf), preservada a partir do repositório público do projeto. Os RF01–RF05 abaixo mantêm os identificadores originais; a situação distingue o planejamento da adaptação acadêmica entregue.

| ID original | Necessidade prevista | Situação na versão atual |
|---|---|---|
| RF01 | Coletar continuamente temperatura, umidade e fumaça de sensores automáticos. | Adaptado: registro manual de leituras simuladas; não há coleta contínua ou IoT. |
| RF02 | Notificar central e brigadistas quando uma medição atingir condição crítica. | Parcial: alerta automático persistido e exibido no terminal; sem notificações externas. |
| RF03 | Gerenciar sensores e sua localização geográfica. | Parcial: CRUD de sensores e consulta de coordenadas de estações; sem mapa/edição de coordenadas dos sensores. |
| RF04 | Autenticar usuários e distinguir permissões dos três perfis. | Implementado e testado com PostgreSQL. |
| RF05 | Consultar histórico de leituras/incidentes como base para análises. | Histórico e relatórios implementados; análise preditiva não implementada. |

As funcionalidades implementadas são identificadas abaixo como FI01–FI10 para não substituir a numeração do planejamento:

| ID | Requisito funcional | Situação |
|---|---|---|
| FI01 | O sistema deve autenticar usuários por e-mail e senha, identificando seu perfil. | Implementado |
| FI02 | O administrador deve cadastrar sensores com estação, nome, tipo e status. | Implementado |
| FI03 | O sistema deve consultar sensores, e permitir ao administrador atualizar nome/status e excluir com confirmação. | CRUD implementado |
| FI04 | Administradores e analistas devem registrar leituras de sensores ativos. | Implementado |
| FI05 | O sistema deve gerar alerta crítico automaticamente para uma leitura que atinja o limite simulado. | Implementado em transação JDBC |
| FI06 | Usuários autenticados devem consultar o histórico de leituras. | Implementado |
| FI07 | O sistema deve listar alertas com sensor, tipo, valor, unidade, criticidade, status, data e responsável. | Implementado |
| FI08 | Administradores e analistas devem encerrar alertas abertos, registrando o responsável autenticado. | Implementado |
| FI09 | Usuários autenticados devem consultar estações com reserva e coordenadas. | Implementado |
| FI10 | Usuários autenticados devem filtrar alertas abertos. | Implementado |

As funcionalidades FI01–FI10 tiveram testes reais de consulta e escrita com os DAOs e o menu em um banco isolado. Veja os resultados e limites na seção Testes.

## Perfis e menu

| Operação | Administrador | Analista | Brigadista |
|---|---|---|---|
| Cadastrar, atualizar e excluir sensores | Sim | Não | Não |
| Registrar leitura | Sim | Sim | Não |
| Encerrar alerta | Sim | Sim | Não |
| Consultar sensores, histórico, alertas e estações | Sim | Sim | Sim |

As permissões são verificadas dentro do switch, antes da entrada de dados e da chamada ao DAO. Perfil desconhecido ou falha de login impedem acesso ao menu.

Opções: 1 cadastrar sensor; 2 listar sensores; 3 atualizar sensor; 4 excluir sensor; 5 registrar leitura; 6 histórico; 7 todos os alertas; 8 encerrar alerta; 9 estações; 10 alertas abertos; **0 sair**. A opção 8 deixou de ser a saída.

Antes de cadastrar um sensor, consulte o ID da estação pela opção 9. IDs não são fixos. A exclusão exige digitar exatamente EXCLUIR seguido do ID, por exemplo EXCLUIR 15, e pode apagar leituras e alertas associados. Não teste essa operação em sensores históricos.

Entradas inválidas cancelam a operação e retornam ao menu. São aceitos ponto ou vírgula decimal. Umidade deve estar entre 0 e 100; fumaça não pode ser negativa; temperatura aceita valores negativos. NaN e infinitos são recusados.

## Programação Orientada a Objetos

DispositivoSensor é abstrata e concentra os atributos protegidos id, nome e valor. SensorTemperatura, SensorUmidade e SensorFumaca herdam essa estrutura e sobrescrevem processarLeitura() com @Override.

LeituraDAO cria a subclasse correspondente ao tipo consultado no banco e mantém uma referência DispositivoSensor. Como o banco armazena NUMERIC(8,2), a decisão de alerta usa o valor arredondado retornado pelo INSERT, mantendo o histórico e o alerta coerentes. Ao chamar sensor.processarLeitura(), a regra executada depende do objeto concreto: isso demonstra polimorfismo real. Usuario encapsula seus dados com atributos privados e getters, sem armazenar senha. DispositivoSensor também mantém uma coleção de objetos Leitura (composição 1:N), usada no registro, com cópia da coleção ao consultar. Essa coleção local não substitui o histórico persistido no banco.

| Tipo | Condição crítica simulada | Unidade |
|---|---|---|
| TEMPERATURA | valor >= 55 | C |
| UMIDADE | valor <= 20 | % |
| FUMACA | valor >= 150 | ppm |

## Banco de dados

As cinco tabelas são TB_ESTACAO, TB_SENSOR, TB_LEITURA, TB_ALERTA e TB_USUARIO. Estações possuem sensores; sensores possuem leituras; leituras originam alertas; um usuário pode ser responsável por vários encerramentos. Um alerta aberto pode não ter responsável, por isso o relatório usa LEFT JOIN.

O registro de uma leitura e seu eventual alerta é uma transação: ambos são confirmados juntos ou desfeitos. O encerramento consulta o alerta com FOR UPDATE, verifica seu status e grava o usuário autenticado na mesma transação. O responsável de um alerta já encerrado é preservado.

A conexão existente usa jdbc:postgresql://localhost:5432/ecoguard e usuário postgres. A senha vem exclusivamente de ECOGUARD_DB_PASSWORD. Nenhum script foi executado no banco existente durante esta revisão.

## Como executar no Windows

### 1. Requisitos

JDK 25, PostgreSQL 18 em execução na porta 5432 e Maven. Não há instalação automática nem configuração global de Path neste projeto. Verifique java -version e mvn -version no seu terminal.

### 2. Banco já existente

Se o banco ecoguard já estiver configurado, **não execute os scripts de criação ou dados iniciais**. Vá diretamente à configuração da senha e à execução da aplicação. Use os usuários acadêmicos existentes.

### 3. Instalação em outro computador

Somente em um PostgreSQL novo, crie um banco vazio chamado ecoguard pelo pgAdmin ou execute:

~~~sql
CREATE DATABASE ecoguard;
~~~

Conecte-se ao banco novo e execute database/script_banco.sql. O script falha se as tabelas já existem e não contém DROP ou TRUNCATE. Sua estrutura foi conferida com o catálogo real do PostgreSQL 18.6 em 11/11/2026: IDs SERIAL, coordenadas NUMERIC(9,6), leituras NUMERIC(8,2), tamanhos, nullabilidade, defaults e relacionamentos correspondentes ao banco existente. O script foi executado e validado somente no banco isolado de testes, nunca no banco principal.

Os dados fictícios opcionais são inseridos por database/dados_iniciais.sql usando psql. Não há senha padrão. Escolha localmente uma senha de demonstração para os três perfis e forneça somente seu hash ao script. No PowerShell:

~~~powershell
$senhaDemo = Read-Host 'Escolha uma senha ficticia para os usuarios de demonstracao' -AsSecureString
$credencialDemo = New-Object System.Net.NetworkCredential('', $senhaDemo)
$bytesDemo = [System.Text.Encoding]::UTF8.GetBytes($credencialDemo.Password)
$algoritmoDemo = [System.Security.Cryptography.SHA256]::Create()
$hashDemo = [System.BitConverter]::ToString($algoritmoDemo.ComputeHash($bytesDemo)).Replace('-', '').ToLowerInvariant()
$algoritmoDemo.Dispose()
[Array]::Clear($bytesDemo, 0, $bytesDemo.Length)
$credencialDemo = $null
psql -h localhost -U postgres -d ecoguard -v ON_ERROR_STOP=1 -f database/script_banco.sql
if ($LASTEXITCODE -ne 0) { throw 'Criacao das tabelas falhou. Nao execute os dados iniciais.' }
psql -h localhost -U postgres -d ecoguard -v ON_ERROR_STOP=1 -v "senha_hash=$hashDemo" -f database/dados_iniciais.sql
$hashDemo = $null
$senhaDemo = $null
~~~

Execute a criação das tabelas apenas uma vez: se já usou o pgAdmin para executá-la, remova a chamada correspondente no exemplo. Caso psql não esteja no Path, use seu caminho completo na instalação do PostgreSQL. Os usuários novos são admin@ecoguard.exemplo, analista@ecoguard.exemplo e brigadista@ecoguard.exemplo; a senha é a escolhida localmente. Isso não altera usuários de outro banco.

### 4. Variável de conexão

No terminal que executará o Java:

~~~powershell
$senhaBanco = Read-Host 'Senha do PostgreSQL' -AsSecureString
$credencialBanco = New-Object System.Net.NetworkCredential('', $senhaBanco)
$env:ECOGUARD_DB_PASSWORD = $credencialBanco.Password
$credencialBanco = $null
$senhaBanco = $null
~~~

Não exiba o conteúdo da variável e não salve senhas em arquivos. A variável vale para esse terminal e seus processos filhos. Ao finalizar, pode removê-la com Remove-Item Env:ECOGUARD_DB_PASSWORD.

### 5. Compilar e executar

~~~powershell
mvn compile
mvn exec:java "-Dexec.mainClass=com.ecoguard.App"
~~~

Se mvn não estiver no Path, use a instalação já existente:

~~~powershell
$mavenEcoGuard = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd'
& $mavenEcoGuard compile exec:java "-Dexec.mainClass=com.ecoguard.App"
~~~

Entre com um usuário do banco e utilize o menu. A senha do login via Scanner aparece ao digitar no terminal; evite compartilhamento de tela durante o login.

Se o Windows/OneDrive bloquear arquivos de target, não é necessário apagar a pasta: use uma saída nova e mantenha o mesmo parâmetro na execução:

~~~powershell
mvn "-Decoguard.build.directory=target/execucao-local" compile exec:java "-Dexec.mainClass=com.ecoguard.App"
~~~

Se o Maven tentar usar C:\.m2 em vez do seu cache, informe o cache correto somente nesse comando: "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository". O modo -o só funciona quando todos os plugins e dependências necessários já estiverem disponíveis localmente.

## Testes

~~~powershell
mvn test
~~~

AppTest executa verificações explícitas por um main, via exec-maven-plugin na fase test. Falhas lançam AssertionError e fazem o Maven falhar. Não requer JUnit nem -ea; Surefire não executa esses testes.

Em 11/11/2026, a compilação e **162 verificações unitárias/simuladas passaram**. Foram testados os limites dos sensores, polimorfismo, composição, entradas inválidas, os três perfis nas dez opções, bloqueio dos DAOs, responsável autenticado, falhas de login/JDBC e saída do menu.

**A integração real foi validada no PostgreSQL 18.6**, usando o PowerShell já configurado do VS Code. A auditoria das cinco tabelas e oito verificações de integridade passou, antes e depois dos testes, com resultados idênticos. Foram preservados 1 estação, 3 sensores, 6 leituras, 3 alertas abertos e 3 usuários.

BancoConsultaTest passou com as tabelas existentes: seis consultas de estrutura, metadados de 31 campos, quatro verificações referenciais, JOINs/filtro de alertas, autenticação inválida e cinco consultas pelos DAOs reais (sensores, estações, histórico e dois relatórios de alertas). A saída dos DAOs foi capturada em memória para não divulgar dados pessoais.

~~~powershell
mvn test-compile exec:java "-Dexec.mainClass=com.ecoguard.BancoConsultaTest" "-Dexec.classpathScope=test"
~~~

AutenticacaoJdbcTest também passou com **fixtures reais em tabelas temporárias**, sem mocks: logins válidos dos três perfis, recusa de senhas incorretas e seis casos de arredondamento NUMERIC(8,2) com regras polimórficas. Hashes foram calculados pelo PostgreSQL e verificados pelo UsuarioDAO. O teste não utiliza sequências de tabelas reais e confirma que o rollback removeu as tabelas temporárias.

~~~powershell
mvn test-compile exec:java "-Dexec.mainClass=com.ecoguard.AutenticacaoJdbcTest" "-Dexec.classpathScope=test"
~~~

A auditoria [database/auditoria_somente_leitura.sql](database/auditoria_somente_leitura.sql) consulta tipos, defaults, constraints, índices, contagens e integridade em transação READ ONLY com ROLLBACK. O psql pode solicitar a senha interativamente:

~~~powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -W -h localhost -p 5432 -U postgres -d ecoguard -v ON_ERROR_STOP=1 -f database/auditoria_somente_leitura.sql
~~~

Não foram gravados nem excluídos dados nas tabelas existentes. **Login com as senhas dos usuários existentes não foi testado nesta etapa**: o teste positivo usou usuários temporários no servidor real. Se desejar verificar um usuário existente, defina ECOGUARD_TEST_EMAIL e ECOGUARD_TEST_PASSWORD somente no terminal antes de BancoConsultaTest; nunca salve ou exiba seus valores.

O teste [FluxoIntegracaoTest](src/test/java/com/ecoguard/FluxoIntegracaoTest.java) agora valida o fluxo completo com os DAOs reais e o menu. Ele cria um banco ecoguard_teste_<identificador aleatório>, executa os dois scripts via psql, autentica os usuários fictícios, verifica CRUD, leituras, alertas, responsável, permissões e rollback. Todos os IDs utilizados vêm das consultas ao banco.

A conexão padrão continua no ecoguard. Somente durante o teste, a propriedade JVM ecoguard.db.url direciona todas as conexões dos DAOs ao banco isolado; ao terminar, ela é restaurada. Uma transação externa não é usada como falsa proteção contra commits dos DAOs. O teste compara assinaturas dos dados e estados das sequências do banco principal, usa um contador de tentativas de DML apenas no banco isolado e remove exclusivamente o banco criado pela própria execução.

~~~powershell
mvn test-compile exec:java "-Dexec.mainClass=com.ecoguard.FluxoIntegracaoTest" "-Dexec.classpathScope=test"
~~~

Esse teste exige que o usuário PostgreSQL tenha permissão para criar e remover o banco de teste. Ele não precisa de novas ferramentas: usa o psql já instalado. O caminho do psql utilizado pelo teste corresponde ao ambiente Windows documentado. A execução final aprovou **178 verificações reais**, além das 162 locais. Os scripts reproduziram campos, defaults, constraints e índices das cinco tabelas do banco principal. As assinaturas dos dados e os estados das cinco sequências principais ficaram idênticos antes/depois; o banco de teste foi removido. O resultado final está em [docs/relatorio-final.md](docs/relatorio-final.md).

As primeiras tentativas de conexão sem variável foram superadas usando o terminal existente. Nenhuma biblioteca foi instalada, nem foi necessário alterar configurações globais. Evidências, correções e limites estão em [docs/revisao-postgresql.md](docs/revisao-postgresql.md); o roteiro está em [docs/testes.md](docs/testes.md).

## Cronograma e integrantes

Cronograma original da primeira entrega, com suas datas, responsáveis e status planejados preservados. A tabela é planejamento, não comprovação de autoria ou de execução em cada prazo.

| Prazo original | Atividade prevista | Responsável no planejamento | Status original |
|---|---|---|---|
| 15/10/2026 | Banco PostgreSQL, DER e scripts | Lorenzzo Michelim (Banco) | Planejado |
| 25/10/2026 | Classes POO e hierarquias de usuários/sensores | Leonardo Gomes (POO) | Planejado |
| 05/11/2026 | CRUD e persistência JDBC/ORM | Vinicius Meggiato (Backend) | Planejado |
| 15/11/2026 | Interface GUI/Terminal e usabilidade | Lorenzzo Michelim | Planejado |
| 25/11/2026 | Integração, documentação e finalização no GitHub | Toda a equipe | Planejado |

A situação técnica verificada em 11/11/2026 está nos relatórios: Java/JDBC, CRUD, terminal e testes operacionais; as adaptações de escopo estão documentadas em [docs/implementacao.md](docs/implementacao.md). A publicação desta versão ainda depende de autorização e associação da pasta ao repositório correto.

Papéis registrados no README original do grupo:

- Lorenzzo Michelim: banco de dados e interfaces.
- Leonardo Gomes: modelagem POO.
- Vinicius Meggiato: backend, CRUD e persistência.

Esses papéis reproduzem o registro original, sem atribuir a qualquer integrante as alterações feitas nesta revisão. A consulta pública do GitHub apresentou apenas lorenzzomichelim com 20 contribuições; isso não comprova contribuições dos demais. O grupo deve registrar suas contribuições reais, sem commits artificiais.

## Limitações e melhorias futuras

As leituras são simuladas, os limites de alerta são fictícios e não há sensores físicos, monitoramento remoto em tempo real, câmeras, mapas gráficos ou análise preditiva. SHA-256 sem salt foi mantido por compatibilidade acadêmica e **não é adequado para armazenar senhas em produção**.

Estações são consultadas, sem cadastro, edição ou exclusão no menu. As coordenadas cadastradas são exibidas numericamente; não há mapa nem edição da localização dos sensores. Usuários são provisionados no banco; não há CRUD de usuários na interface. Melhorias futuras possíveis são essas operações simples, filtros de histórico por período e autenticação com hash adequado, caso o projeto evolua além da demonstração acadêmica.

Veja a correspondência entre implementação, DER e requisitos em [docs/implementacao.md](docs/implementacao.md).
