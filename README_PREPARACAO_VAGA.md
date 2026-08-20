# HighPay - Guia De Preparacao Para Entrevista

Este README foi criado para estudar o projeto HighPay com foco na vaga descrita em `requisito.md`.

A ideia nao e decorar termos. A ideia e entender o suficiente para explicar o projeto com seguranca, relacionando cada decisao tecnica com perguntas que um gestor, tech lead ou arquiteto poderia fazer.

## Pitch Curto Do Projeto

O HighPay e uma simulacao de plataforma de pagamentos PIX.

Ele possui uma API principal em Java e Spring Boot para criar e consultar pagamentos, um worker assincrono que processa eventos via RabbitMQ, PostgreSQL para persistencia, idempotencia para evitar pagamentos duplicados, Transactional Outbox para nao perder eventos, Inbox para evitar reprocessamento duplicado, frontend React, Docker, Kubernetes e observabilidade com metricas, logs correlacionados, Prometheus e Grafana.

Uma forma boa de explicar em entrevista:

```text
Eu construi um fluxo de pagamento com criacao idempotente, persistencia em PostgreSQL, publicacao confiavel de eventos com Transactional Outbox, processamento assincrono via RabbitMQ, deduplicacao no consumidor com Inbox, retry, DLQ, simulacao de provider externo e observabilidade basica.
```

## Como O Projeto Se Conecta Com A Vaga

A vaga pede experiencia com:

- Java;
- Spring Boot;
- microsservicos;
- APIs REST;
- React;
- banco de dados relacional;
- integracao entre sistemas;
- Git e colaboracao;
- testes automatizados;
- Clean Code;
- alta disponibilidade e performance;
- mercado financeiro ou pagamentos;
- modernizacao de legado;
- mensageria e eventos;
- Docker, Kubernetes e cloud;
- CI/CD;
- observabilidade.

O HighPay foi construido justamente para tocar nesses assuntos usando um cenario de pagamentos.

## 1. Java

### O Que E

Java e a linguagem usada no backend. No contexto da vaga, o importante nao e apenas saber sintaxe, mas saber organizar regras de negocio, classes, interfaces, excecoes, enums, testes e integracoes.

### Onde Aparece No HighPay

Java aparece nos tres servicos backend:

```text
backend/payment-service
backend/payment-processor
backend/provider-simulator
```

O projeto usa Java para representar regras de pagamento, como status e transicoes:

```text
CREATED -> PROCESSING -> APPROVED
CREATED -> PROCESSING -> REJECTED
CREATED -> PROCESSING -> FAILED
```

### Pergunta Que Um Gestor Poderia Fazer

```text
Voce usou Java nesse projeto so para CRUD ou existia regra de negocio real?
```

### Resposta Sugerida

```text
Nao foi so CRUD. O projeto simula um fluxo de pagamento, entao eu modelei estados e transicoes permitidas. Um pagamento nasce como CREATED, entra em PROCESSING e depois pode ser aprovado, rejeitado ou falhar. Essas regras ficam no dominio, evitando espalhar regra importante em controller ou SQL.
```

## 2. Spring Boot

### O Que E

Spring Boot e o framework usado para criar aplicacoes Java com servidor HTTP, injecao de dependencia, configuracao, validacao, banco, mensageria, seguranca e observabilidade.

### Onde Aparece No HighPay

O HighPay usa Spring Boot para:

- expor APIs REST;
- executar casos de uso;
- integrar com PostgreSQL via Spring Data JPA;
- integrar com RabbitMQ;
- expor health checks e metricas com Actuator;
- configurar filtros de seguranca;
- propagar correlation id nos logs.

### Pergunta Que Um Gestor Poderia Fazer

```text
Por que usar Spring Boot nesse projeto?
```

### Resposta Sugerida

```text
Porque ele facilita a criacao de servicos backend em Java e integra bem com REST, banco relacional, mensageria, validacao, seguranca e observabilidade. No HighPay eu usei Spring Boot tanto para a API principal quanto para o worker que consome eventos.
```

## 3. APIs REST

### O Que E

API REST e uma forma de expor recursos por HTTP usando rotas, metodos, status codes e JSON.

### Onde Aparece No HighPay

Endpoints publicos:

```http
POST /api/v1/payments
GET /api/v1/payments/{id}
GET /api/v1/payments?page=0&size=20
```

Endpoints internos:

```http
POST /internal/payments/{id}/processing
POST /internal/payments/{id}/approve
POST /internal/payments/{id}/reject
POST /internal/payments/{id}/fail
```

Os endpoints internos sao usados pelo `payment-processor` para atualizar o status do pagamento no `payment-service`.

### Pergunta Que Um Gestor Poderia Fazer

```text
Como voce separou API publica de API interna?
```

### Resposta Sugerida

```text
A API publica recebe chamadas do cliente, como criar e consultar pagamentos. Ja a API interna e usada entre servicos, por exemplo quando o processor precisa marcar um pagamento como aprovado ou rejeitado. Esses endpoints internos exigem um token proprio, porque nao devem ficar disponiveis para usuarios externos.
```

## 4. Microsservicos

### O Que E

Microsservicos sao aplicacoes menores, com responsabilidades separadas, que se comunicam por HTTP, mensageria ou ambos.

### Onde Aparece No HighPay

O projeto possui tres servicos principais:

```text
payment-service
payment-processor
provider-simulator
```

Responsabilidades:

- `payment-service`: recebe requisicoes, cria pagamentos, consulta pagamentos e e dono do agregado `Payment`;
- `payment-processor`: consome eventos e processa pagamentos de forma assincrona;
- `provider-simulator`: simula uma instituicao externa ou provider de pagamento.

### Pergunta Que Um Gestor Poderia Fazer

```text
Por que separar payment-service e payment-processor?
```

### Resposta Sugerida

```text
Porque criar o pagamento e processar com o provider tem caracteristicas diferentes. A criacao precisa responder rapido ao cliente. Ja o processamento pode envolver timeout, falha externa e retry. Separando em um worker assincrono, eu isolo falhas e deixo o fluxo mais resiliente.
```

## 5. React

### O Que E

React e a biblioteca usada para criar interfaces web modernas com componentes, estado e comunicacao com APIs.

### Onde Aparece No HighPay

O frontend fica em:

```text
frontend/
```

Ele funciona como um console operacional para criar, listar e acompanhar pagamentos.

### Pergunta Que Um Gestor Poderia Fazer

```text
Qual foi o papel do React nesse projeto?
```

### Resposta Sugerida

```text
O React foi usado para criar uma interface de acompanhamento dos pagamentos. A ideia nao era fazer apenas uma tela bonita, mas sim consumir a API do payment-service e permitir visualizar o fluxo de criacao e processamento.
```

## 6. Banco Relacional E PostgreSQL

### O Que E

Banco relacional organiza dados em tabelas e permite transacoes, constraints, consultas e integridade dos dados.

Em sistemas financeiros, transacao e consistencia sao assuntos centrais.

### Onde Aparece No HighPay

O `payment-service` usa tabelas como:

```text
payments
outbox_events
```

O `payment-processor` usa:

```text
processed_events
```

O projeto tambem usa Flyway para versionar migrations SQL.

### Pergunta Que Um Gestor Poderia Fazer

```text
Por que usar PostgreSQL em um sistema de pagamento?
```

### Resposta Sugerida

```text
Porque pagamento exige consistencia e rastreabilidade. Com PostgreSQL eu consigo usar transacoes, constraints e historico de dados. No HighPay, por exemplo, a criacao do pagamento e a gravacao do evento no Outbox acontecem na mesma transacao, reduzindo o risco de salvar o pagamento e perder o evento.
```

## 7. Integracao Entre Sistemas

### O Que E

Integracao entre sistemas acontece quando uma aplicacao precisa se comunicar com outra, por HTTP, mensageria, banco ou outros protocolos.

### Onde Aparece No HighPay

O HighPay possui integracoes:

- cliente chama `payment-service` por HTTP;
- `payment-service` publica evento no RabbitMQ;
- `payment-processor` consome evento do RabbitMQ;
- `payment-processor` chama `provider-simulator` por HTTP;
- `payment-processor` chama endpoints internos do `payment-service`.

### Pergunta Que Um Gestor Poderia Fazer

```text
Como voce lidou com falhas na integracao entre sistemas?
```

### Resposta Sugerida

```text
Eu considerei que falhas parciais podem acontecer. Por isso usei processamento assincrono, retry, DLQ, Outbox para nao perder eventos e Inbox para evitar processar mensagem duplicada. Tambem separei o provider externo em um simulador para testar cenarios de sucesso, rejeicao, erro e timeout.
```

## 8. Idempotencia

### O Que E

Idempotencia significa permitir repetir uma mesma operacao sem gerar efeito duplicado.

Em pagamento, isso e essencial.

Exemplo:

```text
Cliente envia pagamento.
Cliente recebe timeout.
Cliente tenta de novo.
Sistema nao pode cobrar duas vezes.
```

### Onde Aparece No HighPay

A criacao de pagamento exige o header:

```http
Idempotency-Key: <chave-unica>
```

Se a mesma chave for usada com o mesmo payload, o sistema retorna o pagamento ja criado.

Se a mesma chave for usada com payload diferente, o sistema retorna conflito.

### Pergunta Que Um Gestor Poderia Fazer

```text
Por que idempotencia e importante em pagamentos?
```

### Resposta Sugerida

```text
Porque clientes e sistemas fazem retry quando ha timeout ou instabilidade. Sem idempotencia, uma segunda tentativa poderia criar outro pagamento e gerar cobranca duplicada. No HighPay eu usei Idempotency-Key para garantir que a repeticao da mesma requisicao retorne o mesmo pagamento.
```

## 9. Transactional Outbox

### O Que E

Transactional Outbox e um padrao usado quando o sistema precisa salvar dados no banco e publicar um evento de forma confiavel.

O problema:

```text
Salvar pagamento no banco
Publicar evento no RabbitMQ
```

Banco e RabbitMQ nao participam da mesma transacao atomica. A aplicacao pode salvar no banco e cair antes de publicar o evento.

### Onde Aparece No HighPay

Ao criar um pagamento, o `payment-service` salva na mesma transacao:

```text
payments
outbox_events
```

Depois, um publisher le eventos pendentes da tabela `outbox_events` e publica no RabbitMQ.

### Pergunta Que Um Gestor Poderia Fazer

```text
Por que voce usou Outbox em vez de publicar direto no RabbitMQ?
```

### Resposta Sugerida

```text
Porque publicar direto cria risco de falha parcial. Se eu salvar o pagamento e a aplicacao cair antes de publicar a mensagem, o pagamento fica parado. Com Outbox, eu salvo o pagamento e a intencao de publicar o evento na mesma transacao. Se a publicacao falhar, o evento continua pendente e pode ser publicado depois.
```

## 10. Mensageria E Arquitetura Orientada A Eventos

### O Que E

Mensageria permite que servicos se comuniquem de forma assincrona por filas e eventos.

Arquitetura orientada a eventos significa que uma mudanca importante no sistema gera um evento, e outros componentes reagem a ele.

### Onde Aparece No HighPay

Fluxo principal:

```text
payment-service cria PaymentCreated
payment-service publica evento no RabbitMQ
payment-processor consome evento
payment-processor processa pagamento
```

RabbitMQ possui fila principal e DLQ.

### Pergunta Que Um Gestor Poderia Fazer

```text
Por que processar pagamento por mensagem em vez de fazer tudo na request HTTP?
```

### Resposta Sugerida

```text
Porque a chamada ao provider externo pode ser lenta ou falhar. Se eu fizer tudo dentro da request, o cliente fica preso ao tempo e instabilidade do provider. Com mensageria, eu respondo rapido que o pagamento foi criado e processo depois de forma mais resiliente, com retry e DLQ.
```

## 11. Inbox No Consumidor

### O Que E

Inbox e um padrao usado no consumidor para registrar eventos ja recebidos e processados.

Ele existe porque filas geralmente trabalham com entrega `at least once`, ou seja, a mesma mensagem pode chegar mais de uma vez.

### Onde Aparece No HighPay

O `payment-processor` usa a tabela:

```text
processed_events
```

Ela controla se um evento esta:

```text
PROCESSING
PROCESSED
FAILED
```

### Pergunta Que Um Gestor Poderia Fazer

```text
Como voce evita processar a mesma mensagem duas vezes?
```

### Resposta Sugerida

```text
Eu uso uma tabela de Inbox chamada processed_events. Quando o processor recebe uma mensagem, ele tenta registrar aquele eventId. Se o evento ja foi processado, ele ignora. Isso evita chamar o provider duplicadamente para o mesmo pagamento.
```

## 12. Retry E DLQ

### O Que E

Retry e a tentativa automatica de repetir uma operacao que falhou.

DLQ, ou Dead Letter Queue, e uma fila para mensagens que falharam repetidamente e precisam de analise ou reprocessamento manual.

### Onde Aparece No HighPay

O RabbitMQ possui:

```text
highpay.payment-created.queue
highpay.payment-created.dlq
```

Tambem existe endpoint interno para reprocessar uma mensagem da DLQ:

```http
POST /internal/rabbitmq/payment-created-dlq/requeue-one
```

### Pergunta Que Um Gestor Poderia Fazer

```text
O que acontece se uma mensagem falhar varias vezes?
```

### Resposta Sugerida

```text
Ela pode ir para uma DLQ. Isso evita que uma mensagem problematica trave a fila principal. Depois, a equipe pode analisar o motivo da falha e reprocessar a mensagem de forma controlada.
```

## 13. Seguranca

### O Que E

Seguranca em APIs envolve autenticacao, autorizacao, protecao de endpoints internos, validacao de entrada e cuidado com segredos.

### Onde Aparece No HighPay

O projeto possui:

- JWT para API publica;
- token interno para comunicacao entre servicos;
- filtros de autenticacao;
- validacao de requests;
- configuracoes por variaveis de ambiente.

### Pergunta Que Um Gestor Poderia Fazer

```text
Como voce protegeu os endpoints internos?
```

### Resposta Sugerida

```text
Os endpoints internos exigem um token proprio enviado pelo servico chamador. A ideia e separar a autenticacao publica, voltada ao cliente, da autenticacao entre servicos. Em producao, eu evoluiria isso para mTLS, service mesh ou identidade gerenciada dependendo da infraestrutura.
```

## 14. Testes Automatizados

### O Que E

Testes automatizados verificam comportamento do sistema sem depender de teste manual repetitivo.

### Onde Aparece No HighPay

O projeto possui testes para:

- use cases;
- regras de dominio;
- controllers;
- filtros de seguranca;
- clients HTTP;
- RabbitMQ config;
- Outbox;
- Inbox;
- retention jobs;
- provider simulator.

### Pergunta Que Um Gestor Poderia Fazer

```text
Que tipo de teste voce escreveu nesse projeto?
```

### Resposta Sugerida

```text
Eu escrevi testes unitarios para regras e use cases, testes de controller para validar API e testes de componentes de infraestrutura como clients HTTP, filtros de seguranca, Outbox e RabbitMQ. A ideia foi cobrir principalmente os pontos de risco do fluxo de pagamento.
```

## 15. Clean Code E Boas Praticas

### O Que E

Clean Code nao significa codigo bonito por estetica. Significa codigo mais claro, testavel e facil de evoluir.

### Onde Aparece No HighPay

O projeto usa separacao por camadas:

```text
domain
application
interfaces
infrastructure
```

Tambem usa portas e adapters:

```text
UseCase -> Porta
Adapter -> Implementacao com tecnologia concreta
```

### Pergunta Que Um Gestor Poderia Fazer

```text
Por que usar Ports & Adapters?
```

### Resposta Sugerida

```text
Porque eu queria que a regra de aplicacao dependesse de contratos, nao diretamente de tecnologias como JPA, RabbitMQ ou Micrometer. Isso facilita testar use cases, trocar detalhes de infraestrutura e manter a regra de negocio mais isolada.
```

## 16. Alta Disponibilidade, Performance E Escalabilidade

### O Que E

Alta disponibilidade significa manter o sistema funcionando mesmo com falhas.

Performance significa responder bem e usar recursos de forma eficiente.

Escalabilidade significa conseguir crescer carga sem redesenhar tudo.

### Onde Aparece No HighPay

O projeto prepara isso com:

- processamento assincrono;
- filas;
- retry;
- DLQ;
- health checks;
- metricas;
- Docker;
- Kubernetes;
- HPA;
- load balancer;
- separacao entre API e worker.

### Pergunta Que Um Gestor Poderia Fazer

```text
Como esse projeto poderia escalar?
```

### Resposta Sugerida

```text
O payment-service pode escalar horizontalmente atras de um load balancer. O payment-processor tambem pode ter multiplas replicas consumindo da fila. O RabbitMQ ajuda a absorver picos, e o Kubernetes/HPA pode ajustar replicas com base em CPU, memoria ou metricas de fila.
```

## 17. Mercado Financeiro E Pagamentos

### O Que E

Sistemas financeiros precisam lidar com consistencia, rastreabilidade, seguranca, idempotencia, falhas parciais e auditoria.

### Onde Aparece No HighPay

O projeto simula problemas comuns de pagamento:

- cliente repete requisicao;
- provider externo demora;
- provider rejeita;
- provider falha;
- mensagem chega duplicada;
- aplicacao cai entre banco e mensageria;
- status precisa ser controlado.

### Pergunta Que Um Gestor Poderia Fazer

```text
O que diferencia esse projeto de um CRUD comum?
```

### Resposta Sugerida

```text
O foco nao e cadastro. O foco e confiabilidade. Eu tratei problemas como pagamento duplicado, falha entre banco e fila, reprocessamento de mensagem, retry, DLQ, estado do pagamento e observabilidade. Sao preocupacoes muito comuns em sistemas financeiros.
```

## 18. Modernizacao De Legado

### O Que E

Modernizar legado nao significa reescrever tudo. Geralmente significa migrar partes do sistema com seguranca, reduzindo risco e mantendo operacao.

### Onde Aparece No HighPay

O projeto possui documentacao sobre modernizacao em:

```text
docs/modernizacao-legado.md
```

O desenho com microsservicos, eventos e camadas permite explicar como quebrar um sistema antigo em partes menores.

### Pergunta Que Um Gestor Poderia Fazer

```text
Como voce migraria um legado de pagamentos para uma arquitetura mais moderna?
```

### Resposta Sugerida

```text
Eu evitaria uma reescrita grande de uma vez. Primeiro identificaria fronteiras de negocio, como criacao de pagamento e processamento. Depois criaria APIs e eventos ao redor do legado, usando padroes como strangler fig para migrar por partes. Tambem colocaria observabilidade e testes para reduzir risco durante a migracao.
```

## 19. Docker E Kubernetes

### O Que E

Docker empacota aplicacoes em containers.

Kubernetes orquestra containers em ambiente distribuido, cuidando de replicas, deploy, health checks, service discovery e escalabilidade.

### Onde Aparece No HighPay

Arquivos Docker:

```text
compose.yaml
compose.prod.yaml
backend/*/Dockerfile
frontend/Dockerfile
```

Arquivos Kubernetes:

```text
k8s/base/
```

### Pergunta Que Um Gestor Poderia Fazer

```text
Por que usar Docker nesse projeto?
```

### Resposta Sugerida

```text
Para padronizar o ambiente de execucao. Com Docker Compose eu consigo subir PostgreSQL, RabbitMQ, backend, frontend, Prometheus e Grafana de forma reproduzivel. Isso reduz diferenca entre ambiente local e ambiente de deploy.
```

Outra pergunta:

```text
Qual seria o papel do Kubernetes?
```

Resposta:

```text
Orquestrar os servicos em producao, com replicas, health checks, service discovery, configuracao, secrets e escalabilidade horizontal.
```

## 20. CI/CD

### O Que E

CI/CD e o processo de automatizar validacao, build, testes e deploy.

### Onde Aparece No HighPay

O projeto possui documentacao sobre CI/CD em:

```text
docs/fullstack-cicd-security.md
```

### Pergunta Que Um Gestor Poderia Fazer

```text
O que voce colocaria em uma pipeline desse projeto?
```

### Resposta Sugerida

```text
Eu colocaria lint e build do frontend, testes do backend, build das imagens Docker, scan basico de seguranca, publicacao das imagens e deploy automatizado para ambiente de homologacao. Para producao, eu manteria aprovacao manual ou estrategia controlada como blue-green ou canary.
```

## 21. Observabilidade

### O Que E

Observabilidade e a capacidade de entender o que esta acontecendo no sistema por logs, metricas e traces.

### Onde Aparece No HighPay

O projeto possui:

- Actuator;
- metricas Micrometer;
- Prometheus;
- Grafana;
- logs com `correlationId`;
- dashboards;
- alertas.

Arquivos importantes:

```text
observability/prometheus/
observability/grafana/
docs/observabilidade-pratica.md
```

### Pergunta Que Um Gestor Poderia Fazer

```text
Como voce investigaria um pagamento que ficou parado?
```

### Resposta Sugerida

```text
Eu olharia primeiro o status do pagamento no payment-service, depois verificaria se existe evento pendente na outbox, se a mensagem foi publicada no RabbitMQ, se caiu na DLQ e se o processor registrou erro. Com correlationId nos logs, eu consigo acompanhar a mesma operacao entre servicos.
```

## Fluxo Principal Para Explicar Na Entrevista

Use este fluxo como base:

```text
1. Cliente chama POST /api/v1/payments com Idempotency-Key.
2. payment-service valida a request.
3. payment-service cria Payment com status CREATED.
4. Na mesma transacao, salva Payment e evento em outbox_events.
5. OutboxPublisher publica o evento PaymentCreated no RabbitMQ.
6. payment-processor consome a mensagem.
7. processor registra o eventId em processed_events para evitar duplicidade.
8. processor marca o pagamento como PROCESSING.
9. processor chama o provider-simulator.
10. provider responde SUCCESS, REJECTED, ERROR, SLOW ou TIMEOUT.
11. processor salva a decisao do provider.
12. processor chama payment-service para aprovar, rejeitar ou falhar o pagamento.
13. metricas e logs permitem acompanhar o fluxo.
```

## Perguntas Fortes Que Podem Aparecer

### 1. Por que esse projeto nao e apenas um CRUD?

```text
Porque ele trata problemas reais de sistemas financeiros: idempotencia, falha parcial, consistencia entre banco e mensageria, processamento assincrono, retry, DLQ, deduplicacao de eventos e observabilidade.
```

### 2. O que acontece se o cliente tentar criar o mesmo pagamento duas vezes?

```text
Se usar a mesma Idempotency-Key com o mesmo payload, o sistema retorna o pagamento ja criado. Se usar a mesma chave com payload diferente, retorna conflito.
```

### 3. O que acontece se o sistema salvar o pagamento mas cair antes de publicar a mensagem?

```text
O evento fica salvo na tabela outbox_events. Quando o sistema voltar, o publisher pode publicar o evento pendente. Isso evita perder o processamento.
```

### 4. O que acontece se o RabbitMQ entregar a mesma mensagem duas vezes?

```text
O payment-processor consulta a tabela processed_events. Se o eventId ja foi processado, ele ignora a duplicidade.
```

### 5. Por que o processor nao atualiza direto a tabela payments?

```text
Porque o payment-service e o dono do agregado Payment. Se outro servico atualizasse direto o banco, poderia violar regras de dominio ou acoplar no schema interno. Por isso o processor chama endpoints internos do payment-service.
```

### 6. Como voce melhoraria esse projeto para producao?

```text
Eu evoluiria autenticacao entre servicos para mTLS ou identidade gerenciada, adicionaria tracing distribuido, melhoraria estrategia de retries com backoff, configuraria secrets reais, usaria banco gerenciado, monitoraria filas e latencia, e colocaria deploy progressivo com rollback.
```

## Resumo Final Para Memorizar

Se precisar resumir o projeto em uma resposta curta:

```text
O HighPay simula uma plataforma de pagamentos com foco em confiabilidade. A API cria pagamentos de forma idempotente, persiste no PostgreSQL e grava eventos com Outbox. O processamento acontece de forma assincrona via RabbitMQ por um worker separado, que usa Inbox para evitar duplicidade, chama um provider externo simulado e atualiza o status do pagamento por endpoints internos. O projeto tambem inclui testes, Docker, Kubernetes, observabilidade e frontend React.
```

