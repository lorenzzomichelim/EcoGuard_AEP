# Implementação e aderência à AEP

## Documentos disponíveis

Foram lidos o guia geral da AEP disponível em src e o texto das sete páginas da primeira entrega, além dos dois diagramas originais. A primeira entrega e as imagens foram recuperadas em modo somente de leitura do [repositório público do grupo](https://github.com/lorenzzomichelim/EcoGuard_AEP) e preservadas localmente:

- [Entrega_Bimestre_1.pdf](Entrega_Bimestre_1.pdf).
- [Diagrama_Classe_EcoGuard.png](Diagrama_Classe_EcoGuard.png).
- [DER_EcoGuard.png](DER_EcoGuard.png).

O PDF confirma os cinco RFs originais, o cronograma de 15/10 a 25/11/2026 e o mesmo link do repositório. Datas e responsáveis originais foram incorporados ao README sem atribuir novas contribuições. As extrações de leitura ficam em target/auditoria e não são versionadas.

## Justificativa técnica

Java permite demonstrar abstração, herança, encapsulamento e polimorfismo diretamente. PostgreSQL mantém os registros entre execuções e suas chaves estrangeiras relacionam estações, sensores, leituras, alertas e usuários. JDBC mostra explicitamente as consultas, parâmetros e transações. Maven organiza compilação e execução. O terminal reduz o escopo visual e permite focar os requisitos acadêmicos. A separação App/model/dao/util é suficiente para esta versão; não há frameworks ou servidor web.

## POO na versão atual

- Abstração: DispositivoSensor declara processarLeitura() abstrato.
- Herança: as três classes de sensor estendem DispositivoSensor.
- Polimorfismo: LeituraDAO usa DispositivoSensor e chama o método sobrescrito com @Override.
- Encapsulamento: Usuario e Leitura têm atributos privados; a coleção de leituras do sensor retorna uma cópia.
- Composição 1:N: DispositivoSensor mantém uma coleção de Leitura, adicionada após o INSERT no fluxo real de registro. O valor processado passa a ser o da última leitura adicionada. A coleção é local ao objeto usado nessa operação, não uma cópia completa do histórico. A persistência e o histórico completo continuam exclusivamente no PostgreSQL.

Diagrama simplificado da implementação atual (não é reprodução do diagrama original):

~~~mermaid
classDiagram
    class DispositivoSensor {
        <<abstract>>
        #int id
        #String nome
        #double valor
        +processarLeitura() boolean
        +adicionarLeitura(Leitura)
        +getLeituras() ArrayList
    }
    DispositivoSensor <|-- SensorTemperatura
    DispositivoSensor <|-- SensorUmidade
    DispositivoSensor <|-- SensorFumaca
    DispositivoSensor "1" *-- "0..*" Leitura
    class Leitura {
        -int id
        -double valor
        -String unidadeMedida
    }
    class Usuario {
        -int id
        -String nome
        -String email
        -String perfil
    }
    LeituraDAO ..> DispositivoSensor
    UsuarioDAO ..> Usuario
    AlertaDAO ..> Usuario
~~~

A multiplicidade 0..* permite um sensor recém-criado sem leituras. Depois do primeiro registro, há uma ou mais leituras relacionadas.

## DER da estrutura de referência

~~~mermaid
erDiagram
    TB_ESTACAO ||--o{ TB_SENSOR : possui
    TB_SENSOR ||--o{ TB_LEITURA : registra
    TB_LEITURA ||--o{ TB_ALERTA : origina
    TB_USUARIO o|--o{ TB_ALERTA : responsavel
~~~

Todas as tabelas possuem ID próprio. As referências são id_estacao em TB_SENSOR, id_sensor em TB_LEITURA, id_leitura e id_usuario_resp em TB_ALERTA. Somente o responsável é opcional. A estrutura de referência evita exclusão de estação com sensores e permite cascata de sensor para leitura e de leitura para alerta. O esquema foi comparado ao catálogo e à exportação somente de estrutura do PostgreSQL 18.6 em 11/11/2026. O script local foi corrigido para SERIAL, NUMERIC(9,6) nas coordenadas, NUMERIC(8,2) na leitura, tamanhos, campos opcionais, defaults e constraints reais. Não foi executado no banco existente.

## Decisões e escopo

O CRUD principal é de sensores. O nome e o status são editáveis; estação e tipo são definidos no cadastro. A localização é consultada pelas coordenadas da estação; o cadastro de sensores preserva o comportamento original sem exigir coordenadas. Cadastro/edição de estações, CRUD de usuários e edição da localização dos sensores ficaram como melhorias futuras.

A autenticação passou de String perfil para Usuario, retornando ID, nome, e-mail e perfil, sem senha. O menu mantém os bloqueios existentes e inclui encerramento de alertas apenas para administrador/analista. O ID responsável vem do login, não de um campo digitado pelo operador.

Leitura e alerta são gravados juntos; mensagens de sucesso só aparecem após commit. Leituras inexistentes/inativas não geram registros. Os limites são os da simulação original. O encerramento preserva alertas inexistentes ou já encerrados e não sobrescreve responsáveis anteriores. Listagens usam PreparedStatement, JOIN e LEFT JOIN no caso do responsável opcional.

## Comparação com a proposta original

| Ponto planejado | Implementação e adaptação |
|---|---|
| RF01: coleta contínua IoT | Leituras simuladas digitadas no terminal e persistidas. A automação física foi adiada. |
| RF02: avisos à central/brigadistas | Alertas automáticos em PostgreSQL/terminal; não há serviço externo de notificação. |
| RF03: gestão/localização de sensores | CRUD completo de sensores; consulta da localização das estações. Não há mapa ou edição da localização individual pelo menu. |
| RF04: autenticação e perfis | Implementado, incluindo testes reais de permissão que detectam tentativas de DML no banco isolado. |
| RF05: histórico para análise | Histórico e alertas implementados. Sem análise preditiva. |
| Hierarquia Usuario→Administrador/Analista/Brigadista | Simplificada para Usuario com campo perfil e ifs no switch. A herança/polimorfismo obrigatórios estão na hierarquia dos sensores. |
| Composição EstacaoMonitoramento→sensores | O vínculo estação–sensor está na FK; a composição Java demonstrada é DispositivoSensor→Leitura. EstacaoMonitoramento não é classe da versão atual. |
| processarLeitura retornando Leitura e validarAlerta separado | processarLeitura retorna boolean e concentra a regra crítica; o registro fica no DAO. |
| Classe Alerta com notificarBrigadistas | Persistência/encerramento em AlertaDAO e consulta em RelatorioDAO; sem entidade Java Alerta ou notificações externas. |
| PostGIS e concorrência de sensores | Não necessários à simulação: coordenadas NUMERIC e execução sequencial com Scanner. |
| Estrutura controller/model/view | Adaptada à estrutura Maven App/model/dao/util, com JDBC direto. |

O DER original apresenta as mesmas cinco entidades e os principais tipos corretos. O banco atual inclui nome em TB_SENSOR, ausente no desenho. A ligação leitura–alerta no desenho é 0..1; a aplicação gera no máximo um alerta por registro, mas a FK não possui UNIQUE e o banco permite múltiplos alertas para uma leitura. O responsável é opcional no catálogo real, conforme o LEFT JOIN. Os diagramas originais não foram alterados; os diagramas Mermaid deste documento descrevem a implementação atual.

## Validação técnica final

O FluxoIntegracaoTest executou a instalação dos dois scripts num banco isolado, comparou campos/defaults/constraints/índices com o principal e aprovou 178 verificações reais de CRUD, leituras, geração/encerramento de alertas, responsável, menu, permissões, validações e rollback. O ecoguard teve suas assinaturas de dados e estados de sequências preservados. O banco isolado foi removido após o teste. As 162 verificações locais e os testes JDBC anteriores também passaram.

## Pendências acadêmicas e de publicação

1. O grupo deve apresentar as adaptações acima de forma explícita; não afirmar que há IoT, mapas, notificações externas ou previsão de incêndios. A aceitação acadêmica dessas adaptações depende da avaliação do professor.
2. A pasta local não possui .git. O repositório público correto foi confirmado, mas contém ainda a primeira entrega; a versão Java/Maven validada não foi publicada. Não foi instalado Git nem criado histórico local artificial.
3. O planejamento registra Lorenzzo Michelim, Leonardo Gomes e Vinicius Meggiato. Os papéis foram preservados como planejamento; a consulta pública de contribuições apresentou apenas a conta lorenzzomichelim com 20 contribuições. A autoria real dos demais deve ser comprovada pelo grupo, sem inventar commits.
4. Commits e push precisam de autorização. Não há pendência técnica conhecida de compilação, scripts ou fluxos funcionais cobertos nesta validação.

O projeto atende tecnicamente aos pilares, CRUD e persistência da AEP dentro da simulação implementada. Não se declara aprovação acadêmica nem aderência integral às funcionalidades físicas previstas na primeira entrega. O resultado detalhado está em [relatorio-final.md](relatorio-final.md).
