# HighPay - Documentacao de Arquitetura e Fluxos

Este documento explica o estado atual do projeto HighPay, os servicos envolvidos e os principais fluxos de execucao.

A ideia central do projeto e simular uma plataforma de pagamentos PIX com preocupacoes reais de sistemas financeiros:

- idempotencia;
- consistencia transacional;
- processamento assincrono;
- mensageria;
- transactional outbox;
- separacao em camadas;
- tolerancia inicial a falhas;
- evolucao para alta disponibilidade.

## 1. Servicos Atuais

Hoje o projeto possui tres servicos principais:

```text
backend/
  payment-service/
  payment-processor/
  provider-simulator/
```

### payment-service

Responsavel por:

- receber requisicoes HTTP publicas de pagamento;
- criar pagamentos;
- consultar pagamentos;
- listar pagamentos com paginacao;
- garantir idempotencia na criacao;
- persistir pagamentos no PostgreSQL;
- gravar eventos na tabela de outbox;
- publicar eventos pendentes no RabbitMQ;
- expor endpoints internos para atualizacao de status.

Porta HTTP padrao:

```text
8081
```

Durante o teste ponta a ponta foi usada uma segunda instancia em:

```text
8084
```

### payment-processor

Responsavel por:

- consumir eventos `payment.created` do RabbitMQ;
- interpretar o payload do evento;
- avisar o payment-service que o pagamento entrou em processamento;
- chamar o provider-simulator;
- aprovar, rejeitar ou falhar o pagamento no payment-service.

Porta HTTP padrao:

```text
8082
```

Hoje ele ainda nao expoe endpoints publicos relevantes. Ele trabalha principalmente como consumidor RabbitMQ.

### provider-simulator

Responsavel por simular uma instituicao financeira externa.

Porta HTTP padrao:

```text
8083
```

Endpoint principal:

```text
POST /api/v1/provider/payments
```

Ele pode simular estes cenarios:

```text
SUCCESS
REJECTED
ERROR
SLOW
TIMEOUT
```

## 2. Infraestrutura Local

A infraestrutura local usa Docker Compose para:

```text
PostgreSQL
RabbitMQ
```

Arquivos principais:

```text
compose.yaml
.env
```

Servicos Docker:

```text
highpay-postgres
highpay-rabbitmq
```

Portas:

```text
PostgreSQL: 5432
RabbitMQ AMQP: 5672
RabbitMQ Management UI: 15672
```

Credenciais esperadas pelo projeto:

```text
PostgreSQL
  database: highpay
  user: highpay
  password: highpay_local_password

RabbitMQ
  user: highpay
  password: highpay_local_password
```

Durante a implementacao, o RabbitMQ local possuia apenas o usuario `guest`. Foi criado o usuario `highpay` e dadas permissoes no vhost `/`.

## 3. Arquitetura Em Camadas

O projeto usa uma arquitetura inspirada em Ports & Adapters, tambem chamada de Hexagonal Architecture.

A regra geral e:

```text
domain
  regras de negocio e entidades principais

application
  casos de uso e portas

interfaces
  entrada HTTP / REST

infrastructure
  banco, RabbitMQ, HTTP clients, frameworks externos
```

## 4. payment-service: Estrutura Atual

Estrutura principal:

```text
com.highpay.payment
  application
    exception
    port
    usecase

  domain
    enums
    model

  infrastructure
    messaging
    persistence

  interfaces
    rest
```

### Domain

Contem:

```text
Payment
PaymentMethod
PaymentStatus
```

`Payment` representa o pagamento e controla transicoes de estado:

```text
CREATED -> PROCESSING -> APPROVED
CREATED -> PROCESSING -> REJECTED
CREATED -> PROCESSING -> FAILED
```

Metodos importantes:

```java
markAsProcessing()
approve(providerTransactionId)
reject(providerTransactionId)
fail()
```

Esses metodos protegem a regra de negocio: por exemplo, um pagamento so pode ser aprovado se estiver em `PROCESSING`.

### Application

Contem os casos de uso:

```text
CreatePaymentUseCase
GetPaymentUseCase
ListPaymentsUseCase
MarkPaymentAsProcessingUseCase
ApprovePaymentUseCase
RejectPaymentUseCase
FailPaymentUseCase
```

Tambem contem portas:

```text
PaymentRepository
OutboxEventRepository
```

Porta significa contrato que a aplicacao precisa, sem saber qual tecnologia implementa.

Exemplo:

```java
public interface PaymentRepository {
    Optional<Payment> findById(UUID id);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    ListPaymentsResult findAll(int page, int size);
    Payment save(Payment payment);
}
```

O use case depende da interface, nao de JPA.

### Infrastructure

Contem implementacoes tecnicas:

```text
JpaPaymentRepository
PaymentRepositoryAdapter
OutboxEventEntity
JpaOutboxEventRepository
OutboxEventRepositoryAdapter
RabbitMqConfig
OutboxPublisher
```

Aqui ficam detalhes de:

- Spring Data JPA;
- PostgreSQL;
- RabbitMQ;
- agendamento do publisher.

### Interfaces REST

Contem controllers, requests, responses e tratamento de erro:

```text
PaymentController
InternalPaymentController
CreatePaymentRequest
PaymentResponse
PaymentPageResponse
GlobalExceptionHandler
ApiErrorResponse
FieldErrorResponse
```

## 5. Fluxo 1 - Criacao de Pagamento

Endpoint publico:

```text
POST /api/v1/payments
```

Header obrigatorio:

```text
Idempotency-Key: <chave-unica-gerada-pelo-cliente>
```

Body exemplo:

```json
{
  "merchantId": "merchant-001",
  "amount": 100.00,
  "currency": "BRL",
  "paymentMethod": "PIX"
}
```

Fluxo:

```text
Cliente
  -> PaymentController
  -> CreatePaymentUseCase
  -> PaymentRepository.findByIdempotencyKey
```

Se a chave nao existe:

```text
Payment.create(...)
  -> status CREATED
  -> PaymentRepository.save(payment)
  -> OutboxEventRepository.savePaymentCreatedEvent(payment)
  -> commit da transacao
  -> retorna 201 Created
```

Se a chave ja existe:

```text
retorna pagamento existente
nao salva novo Payment
nao cria novo outbox_event
retorna 200 OK
```

Importante:

```text
Idempotency-Key e gerada pelo cliente que iniciou a operacao.
```

O backend nao deve gerar essa chave para o cliente, porque ela serve para reconhecer retries da mesma tentativa.

## 6. Fluxo 2 - Idempotencia

Exemplo:

```text
Request 1
Idempotency-Key: ABC
amount: 100

Request 2
Idempotency-Key: ABC
amount: 100
```

Resultado esperado:

```text
Request 1
  -> cria Payment #1
  -> 201 Created

Request 2
  -> encontra Payment #1
  -> 200 OK
```

O banco possui constraint unica:

`````sql
UNIQUE (idempotency_key)
```

Isso protege tambem contra concorrencia entre instancias.

Ponto futuro ainda nao resolvido:

```text
Mesma Idempotency-Key com payload diferente.
```

Exemplo:

```text
Request 1: key ABC, amount 100
Request 2: key ABC, amount 999
```

Agora o sistema persiste um fingerprint da request original. Se a mesma Idempotency-Key for reutilizada com payload diferente, a request e rejeitada com conflito.

## 7. Fluxo 3 - Consulta Por ID

Endpoint:

```text
GET /api/v1/payments/{id}
```

Fluxo:

```text
PaymentController
  -> GetPaymentUseCase
  -> PaymentRepository.findById
  -> PaymentRepositoryAdapter
  -> JpaPaymentRepository
  -> PostgreSQL
```

Se encontrar:

```text
200 OK
PaymentResponse
```

Se nao encontrar:

```text
404 Not Found
```

A decisao `404` fica no controller porque e uma semantica HTTP.

## 8. Fluxo 4 - Listagem Paginada

Endpoint:

```text
GET /api/v1/payments?page=0&size=20
```

Resposta:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Fluxo:

```text
PaymentController
  -> ListPaymentsUseCase
  -> PaymentRepository.findAll(page, size)
  -> PaymentRepositoryAdapter
  -> JpaPaymentRepository.findAll(PageRequest)
```

O use case valida:

```text
page >= 0
size entre 1 e 100
```

A porta nao retorna `Page` do Spring Data. Ela retorna:

```text
ListPaymentsResult
```

Isso evita acoplar a camada `application` ao Spring Data.

## 9. Fluxo 5 - Tratamento Padronizado De Erros

O tratamento global fica em:

```text
GlobalExceptionHandler
```

Ele usa:

```java
@RestControllerAdvice
```

Isso permite tratar excecoes lancadas por qualquer controller REST.

Formato padrao:

```json
{
  "timestamp": "2026-08-13T17:23:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/v1/payments",
  "fieldErrors": [
    {
      "field": "amount",
      "message": "Amount must be greater than zero"
    }
  ]
}
```

Erros tratados:

```text
MethodArgumentNotValidException -> 400
MissingRequestHeaderException -> 400
MethodArgumentTypeMismatchException -> 400
PaymentNotFoundException -> 404
IllegalArgumentException -> 400
IllegalStateException -> 409
Exception -> 500
```

## 10. Fluxo 6 - Transactional Outbox

Problema que o outbox resolve:

```text
salvar Payment no banco
publicar evento no RabbitMQ
```

Sem outbox, poderia acontecer:

```text
INSERT payments OK
COMMIT OK
aplicacao morre antes de publicar no RabbitMQ
```

Resultado ruim:

```text
Payment existe
mas nenhum consumidor foi avisado
```

Com outbox:

```text
BEGIN
INSERT payments
INSERT outbox_events
COMMIT
```

Se a aplicacao morrer depois, o evento continua no banco como `PENDING`.

Tabela:

```text
outbox_events
  id
  aggregate_id
  aggregate_type
  event_type
  payload
  status
  retry_count
  created_at
  published_at
```

Estados atuais:

```text
PENDING
PUBLISHED
FAILED
```

Quando um pagamento novo e criado, o sistema grava:

```text
payments.status = CREATED
outbox_events.status = PENDING
outbox_events.event_type = PaymentCreated
```

## 11. Fluxo 7 - OutboxPublisher

O `OutboxPublisher` fica em:

```text
payment-service/infrastructure/messaging/outbox
```

Ele e infraestrutura porque conhece:

```text
JPA/PostgreSQL
RabbitMQ
```

Fluxo:

```text
OutboxPublisher
  -> busca eventos PENDING
  -> publica no RabbitMQ
  -> marca evento como PUBLISHED
```

Configuracoes:

```properties
highpay.outbox.publisher.enabled=false
highpay.outbox.publisher.fixed-delay-ms=5000
highpay.outbox.publisher.batch-size=20
```

Por padrao esta desligado:

```text
highpay.outbox.publisher.enabled=false
```

Motivo:

- nao publicar automaticamente durante testes;
- nao depender do RabbitMQ em todos os ambientes;
- permitir ativacao explicita local/producao.

Para ativar:

```properties
highpay.outbox.publisher.enabled=true
```

## 12. RabbitMQ

Configuracao atual:

```properties
highpay.rabbitmq.payment-exchange=highpay.payments.exchange
highpay.rabbitmq.payment-created-routing-key=payment.created
highpay.rabbitmq.payment-created-queue=highpay.payment-created.queue
```

Topologia:

```text
DirectExchange: highpay.payments.exchange
Routing Key: payment.created
Queue: highpay.payment-created.queue
```

Fluxo:

```text
OutboxPublisher
  -> exchange highpay.payments.exchange
  -> routing key payment.created
  -> queue highpay.payment-created.queue
  -> payment-processor
```

## 13. Fluxo 8 - payment-processor Consumindo Evento

Classe:

```text
PaymentCreatedListener
```

Ela escuta:

```java
@RabbitListener(queues = "${highpay.rabbitmq.payment-created-queue}")
```

Fluxo:

```text
RabbitMQ entrega payload
  -> PaymentCreatedListener.handle(payload)
  -> ProcessPaymentCreatedUseCase.execute(payload)
```

O use case:

1. valida se o payload nao esta vazio;
2. extrai `paymentId`, `amount`, `currency`;
3. chama o payment-service para marcar `PROCESSING`;
4. chama o provider-simulator;
5. aprova, rejeita ou falha o pagamento.

## 14. Fluxo 9 - Processor Chamando payment-service

O processor nao acessa diretamente o banco do payment-service.

Ele chama endpoints internos HTTP:

```text
POST /internal/payments/{id}/processing
POST /internal/payments/{id}/approve
POST /internal/payments/{id}/reject
POST /internal/payments/{id}/fail
```

Essas rotas ficam protegidas por autenticacao service-to-service simples.

Header exigido:

```text
X-Internal-Service-Token: <shared-secret>
```

Configuracao:

```properties
payment-service:
highpay.internal-auth.token=${HIGHPAY_INTERNAL_AUTH_TOKEN:highpay_internal_local_token}

payment-processor:
highpay.payment-service.internal-auth-token=${HIGHPAY_INTERNAL_AUTH_TOKEN:highpay_internal_local_token}
```

Se o header estiver ausente ou diferente do segredo configurado, o `payment-service` responde `401 Unauthorized` antes de executar o controller interno.

Por que nao acessar o banco diretamente?

Porque o dono do agregado `Payment` e o `payment-service`.

Se o processor alterasse a tabela diretamente, ele poderia violar regras de dominio ou gerar acoplamento forte com o schema.

Fluxo correto:

```text
payment-processor
  -> HTTP interno
  -> payment-service
  -> use case
  -> Payment.markAsProcessing/approve/reject/fail
  -> PostgreSQL
```

## 15. Fluxo 10 - Processor Chamando Provider

O processor chama:

```text
POST http://localhost:8083/api/v1/provider/payments
```

Body:

```json
{
  "paymentId": "...",
  "amount": 100.00,
  "currency": "BRL"
}
```

Resposta de sucesso:

```json
{
  "status": "SUCCESS",
  "providerTransactionId": "provider-...",
  "message": "Payment approved by provider"
}
```

Resposta de rejeicao:

```json
{
  "status": "REJECTED",
  "providerTransactionId": "provider-...",
  "message": "Payment rejected by provider"
}
```

Se o provider retornar erro HTTP, o processor interpreta como erro e marca o pagamento como `FAILED`.

## 16. Fluxo 11 - Provider Simulator

Endpoint:

```text
POST /api/v1/provider/payments
```

Header opcional:

```text
X-Provider-Scenario: SUCCESS | REJECTED | ERROR | SLOW | TIMEOUT
```

Se o header nao vier, usa:

```properties
highpay.provider.default-scenario=SUCCESS
```

Cenarios:

```text
SUCCESS
  -> 200 OK, status SUCCESS

REJECTED
  -> 200 OK, status REJECTED

ERROR
  -> 500 Internal Server Error

SLOW
  -> espera alguns ms e responde SUCCESS

TIMEOUT
  -> espera mais tempo e responde SUCCESS, permitindo testar timeout no caller
```

## 17. Fluxo Ponta a Ponta Atual

Fluxo completo:

```text
Cliente
  -> POST /api/v1/payments

payment-service
  -> cria Payment CREATED
  -> grava outbox_event PENDING

OutboxPublisher
  -> busca outbox_event PENDING
  -> publica no RabbitMQ
  -> marca outbox_event PUBLISHED

RabbitMQ
  -> entrega mensagem para fila highpay.payment-created.queue

payment-processor
  -> consome mensagem
  -> chama payment-service para PROCESSING
  -> chama provider-simulator

provider-simulator
  -> retorna SUCCESS

payment-processor
  -> chama payment-service para APPROVED

payment-service
  -> atualiza Payment APPROVED
```

Resultado observado no teste real:

```json
{
  "initialStatus": "CREATED",
  "finalStatus": "APPROVED",
  "providerTransactionId": "provider-846bd130-9d08-44cf-a28d-01b655532b1f"
}
```

## 18. Comandos De Teste

### payment-service

```powershell
cd backend/payment-service
.\mvnw.cmd test
```

### payment-processor

```powershell
cd backend/payment-processor
.\mvnw.cmd test
```

### provider-simulator

```powershell
cd backend/provider-simulator
.\mvnw.cmd test
```

## 19. Como Rodar Localmente O Fluxo Completo

Subir infra:

```powershell
docker compose up -d
```

Rodar provider:

```powershell
cd backend/provider-simulator
.\mvnw.cmd spring-boot:run
```

Rodar payment-service com publisher ativo:

```powershell
cd backend/payment-service
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--highpay.outbox.publisher.enabled=true"
```

Rodar processor:

```powershell
cd backend/payment-processor
.\mvnw.cmd spring-boot:run
```

Criar pagamento:

```powershell
$body = @{
  merchantId = "merchant-001"
  amount = 100.00
  currency = "BRL"
  paymentMethod = "PIX"
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri http://localhost:8081/api/v1/payments `
  -Method Post `
  -Headers @{ "Idempotency-Key" = [guid]::NewGuid().ToString() } `
  -ContentType "application/json" `
  -Body $body
```

Consultar pagamento:

```powershell
Invoke-RestMethod -Uri http://localhost:8081/api/v1/payments/{id}
```

## 20. Decisoes Arquiteturais Importantes

### 20.1 payment-service e dono do Payment

O processor nao altera a tabela `payments` diretamente.

Motivo:

```text
Payment e agregado do payment-service.
As regras de transicao de status estao no dominio do payment-service.
```

### 20.2 Outbox antes de Rabbit direto

O payment-service nao publica direto no RabbitMQ dentro do fluxo de criacao.

Ele grava primeiro no banco:

```text
Payment
OutboxEvent
```

Depois o publisher publica.

Motivo:

```text
evitar perda de evento se a aplicacao cair entre commit do banco e publish no broker
```

### 20.3 Use cases dependem de portas

Exemplo:

```text
CreatePaymentUseCase
  -> PaymentRepository
  -> OutboxEventRepository
```

Ele nao depende de `JpaRepository`.

Motivo:

```text
separar regra de aplicacao de tecnologia de persistencia
```

### 20.4 Adapters implementam portas

Exemplo:

```text
PaymentRepositoryAdapter implements PaymentRepository
OutboxEventRepositoryAdapter implements OutboxEventRepository
```

Motivo:

```text
adaptar tecnologia externa ao contrato que a aplicacao entende
```

### 20.5 Provider simulator separado

O provider externo foi modelado como outro servico.

Motivo:

```text
simular integracao real com instituicao externa
permitir testar sucesso, rejeicao, erro, lentidao e timeout
```

## 21. Limitacoes Atuais

O fluxo principal ja cobre idempotencia HTTP, Outbox, Inbox, retry, DLQ, metricas, correlation id, autenticacao interna, JSON estruturado, timeout, circuit breaker simples, requeue manual de DLQ e protecao do OutboxPublisher contra publicacao concorrente por multiplas instancias.

Ainda faltam pontos para aproximar mais de producao:

- remover segredos locais fracos do `.env` usado em desenvolvimento;
- adicionar TLS/mTLS ou gateway interno para substituir o shared secret simples;
- criar dashboards/alertas em Prometheus/Grafana;
- automatizar limpeza ou arquivamento de registros antigos de outbox/inbox;
- testar cenarios de concorrencia com carga real e multiplas instancias em ambiente dedicado.

## 22. Proximos Passos Recomendados

Ordem sugerida a partir do estado atual:

```text
1. Dashboards e alertas com Prometheus/Grafana
2. TLS/mTLS ou gateway interno para substituir shared secret simples
3. Politica de retencao para outbox_events e processed_events
4. Teste de carga com multiplas instancias dos servicos
5. Runbook operacional para DLQ, reprocessamento e investigacao
```

## 23. Resumo Mental

Resumo do sistema em uma linha:

```text
API cria pagamento com idempotencia, grava evento em outbox, publica no RabbitMQ, processor consome, chama provider e atualiza status no payment-service.
```

Resumo visual:

```text
Cliente
  -> payment-service
      -> PostgreSQL payments
      -> PostgreSQL outbox_events
      -> RabbitMQ
          -> payment-processor
              -> provider-simulator
              -> payment-service internal endpoints
                  -> PostgreSQL payments atualizado
```
## 24. Observabilidade Implementada Agora

A observabilidade de negocio comecou a ser implementada no `payment-service` usando Micrometer.

Foi criada a porta:

```text
PaymentMetrics
```

Ela fica na camada `application/port` porque os casos de uso querem registrar fatos de negocio, mas nao devem depender diretamente de Micrometer.

Implementacao tecnica:

```text
MicrometerPaymentMetrics
```

Ela fica em:

```text
infrastructure/observability
```

Motivo:

```text
Micrometer e detalhe de infraestrutura/observabilidade.
```

Metricas criadas:

```text
highpay_payment_created_total
highpay_payment_idempotency_hit_total
highpay_payment_processing_started_total
highpay_payment_approved_total
highpay_payment_rejected_total
highpay_payment_failed_total
```

Onde sao registradas:

```text
CreatePaymentUseCase
  -> payment_created_total
  -> payment_idempotency_hit_total

MarkPaymentAsProcessingUseCase
  -> payment_processing_started_total

ApprovePaymentUseCase
  -> payment_approved_total

RejectPaymentUseCase
  -> payment_rejected_total

FailPaymentUseCase
  -> payment_failed_total
```

Decisao arquitetural:

```text
Use case chama PaymentMetrics.
MicrometerPaymentMetrics implementa PaymentMetrics.
```

Assim os casos de uso continuam sem depender de `MeterRegistry`, `Counter` ou qualquer API especifica de observabilidade.

Hoje essas metricas ficam disponiveis no registry interno do Actuator/Micrometer. Para expor em formato Prometheus, ainda precisamos adicionar suporte ao endpoint `/actuator/prometheus` e configurar Prometheus/Grafana.

## 26. Deduplicacao de Eventos no Processor

Agora o evento PaymentCreated carrega dois identificadores diferentes:

- eventId: identifica a mensagem/evento gerado pelo Outbox.
- paymentId: identifica o pagamento de negocio.

Isso importa porque uma mesma mensagem pode ser entregue mais de uma vez pelo RabbitMQ. Em sistemas com fila, a regra pratica e assumir entrega t least once, ou seja: a mensagem chega pelo menos uma vez, mas pode chegar repetida.

O fluxo ficou assim:

`	ext
payment-service
  -> cria pagamento
  -> cria outbox_events.id
  -> coloca esse id no payload como eventId
  -> publica no RabbitMQ

payment-processor
  -> recebe payload
  -> le eventId
  -> consulta processed_events
  -> se ja existe, ignora a mensagem
  -> se nao existe, processa no provider
  -> atualiza pagamento
  -> grava processed_events(event_id, payment_id, processed_at)
`

A tabela nova e:

```sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
`

Essa tabela nao substitui o Outbox. Ela resolve outro problema.

- outbox_events: controla o que o payment-service precisa publicar.
- processed_events: controla o que o payment-processor ja consumiu com sucesso.

Em outras palavras:

`	ext
outbox_events = lado de quem envia
processed_events = lado de quem recebe
`

Tambem foi criada a metrica:

- highpay_processor_duplicate_event_skipped_total

Ela aumenta quando o processor recebe uma mensagem cujo eventId ja esta na tabela processed_events.

O processor usa Flyway tambem, mas com uma tabela de historico separada:

`properties
spring.flyway.table=flyway_schema_history_processor
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
`

Essa decisao evita conflito com o historico de migrations do payment-service, ja que os dois servicos estao usando o mesmo banco local neste projeto.
## 27. Retry do Outbox Publisher

O Outbox agora tem retry controlado quando a publicacao no RabbitMQ falha.

Antes, o comportamento era insuficiente:

`	ext
publisher tenta publicar
  -> RabbitMQ falha
  -> evento vira FAILED
  -> publisher nao busca FAILED
  -> evento fica parado para sempre
`

Agora o comportamento ficou assim:

`	ext
publisher busca eventos PENDING
  -> tenta publicar no RabbitMQ
  -> se publicar, marca PUBLISHED
  -> se falhar, incrementa retry_count
  -> se retry_count ainda esta abaixo do limite, mantem PENDING
  -> se retry_count chegou no limite, marca FAILED
`

A propriedade nova e:

`properties
highpay.outbox.publisher.max-retry-attempts=5
`

Isso significa que o evento continua elegivel para novas tentativas enquanto nao atingiu o limite.

A decisao importante aqui e nao relancar a exception dentro do loop do publisher. Se a exception fosse relancada dentro da transacao, a alteracao de etry_count e status poderia ser desfeita pelo rollback. Agora a falha e registrada no proprio evento e o scheduler pode tentar novamente no proximo ciclo.

Resumo:

- PENDING: ainda vai ser tentado.
- PUBLISHED: ja foi enviado ao RabbitMQ.
- FAILED: esgotou as tentativas automaticas e precisa de acao operacional.

Foram adicionados testes para:

- publicacao com sucesso;
- falha antes do limite, mantendo PENDING;
- falha atingindo o limite, marcando FAILED.
## 28. Fingerprint da Idempotencia HTTP

A idempotencia agora valida nao apenas a chave, mas tambem a intencao original da request.

Antes, se o cliente fizesse:

`	ext
POST /payments
Idempotency-Key: abc
amount: 100.00
`

e depois repetisse a mesma chave com outro payload:

`	ext
POST /payments
Idempotency-Key: abc
amount: 200.00
`

o sistema poderia retornar o pagamento antigo como se fosse uma repeticao valida. Isso e perigoso porque mascara erro do cliente.

Agora o Payment salva equest_fingerprint, um SHA-256 calculado a partir dos campos que representam a intencao de criacao:

`	ext
merchantId | amount | currency | paymentMethod
`

O fluxo ficou assim:

`	ext
CreatePaymentUseCase
  -> busca por Idempotency-Key
  -> se nao existe, cria Payment com request_fingerprint e cria Outbox
  -> se existe, compara fingerprint
  -> se fingerprint igual, retorna o pagamento existente
  -> se fingerprint diferente, rejeita com conflito
`

A migration adicionada foi:

```sql
ALTER TABLE payments
    ADD COLUMN request_fingerprint VARCHAR(64) NOT NULL DEFAULT '';
`

Para registros antigos, o codigo ainda consegue comparar pelos campos salvos quando o fingerprint esta vazio. Para registros novos, a comparacao usa o hash.

Decisao arquitetural:

- A chave idempotente identifica a tentativa do cliente.
- O fingerprint protege a semantica dessa tentativa.
- A combinacao evita duplicidade e tambem evita reutilizacao incorreta da mesma chave.
## 29. Resposta HTTP para Conflito de Idempotencia

A reutilizacao incorreta de uma Idempotency-Key agora aparece na API como 409 Conflict.

Exemplo de erro:

`json
{
  "status": 409,
  "error": "Conflict",
  "message": "Idempotency key was already used with a different payment request",
  "path": "/api/v1/payments"
}
`

Esse status foi escolhido porque a request e valida em formato, mas conflita com uma operacao anterior identificada pela mesma chave idempotente.

A diferenca pratica e:

`	ext
400 Bad Request
  -> request malformada ou campo invalido

409 Conflict
  -> request bem formada, mas entra em conflito com estado ja existente
`

Foi adicionado teste de controller garantindo esse contrato HTTP.
## 30. Inbox com Status no Processor

O processed_events evoluiu de uma tabela simples de historico para um pequeno Inbox do lado consumidor.

A tabela agora controla tres estados:

`	ext
PROCESSING
  -> o processor reservou o evento e esta processando

PROCESSED
  -> o evento ja terminou com sucesso e nao deve rodar de novo

FAILED
  -> uma tentativa falhou e o evento pode ser tentado novamente
`

A migration adicionada foi:

```sql
ALTER TABLE processed_events
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PROCESSED';

CREATE INDEX idx_processed_events_status
    ON processed_events (status);
`

O fluxo ficou assim:

`	ext
processor recebe PaymentCreated
  -> tenta reservar eventId em processed_events
  -> se ja esta PROCESSING, ignora duplicata concorrente
  -> se ja esta PROCESSED, ignora duplicata concluida
  -> se esta FAILED, muda para PROCESSING e tenta de novo
  -> chama payment-service/provider
  -> sucesso: marca PROCESSED
  -> exception: marca FAILED e relanca exception
`

Essa decisao corrige um problema sutil: apenas consultar processed_events no final nao impede duas entregas simultaneas da mesma mensagem de chamarem o provider ao mesmo tempo.

Agora o controle acontece antes da chamada externa.

Ponto importante de arquitetura:

- Nao colocamos uma transacao longa envolvendo chamada HTTP externa.
- O adapter de persistencia usa transacoes curtas para reservar, concluir ou falhar o evento.
- Isso reduz tempo de lock no banco e ainda protege contra duplicidade concorrente.
## 31. Falha ao Notificar Payment-Service

O processor agora trata melhor um erro secundario importante.

Cenario:

`	ext
processor chama provider
  -> provider falha
  -> processor tenta marcar pagamento como FAILED no payment-service
  -> payment-service tambem falha
`

Antes, a segunda falha poderia mascarar a primeira. Isso atrapalha debug porque a causa principal poderia ter sido o provider, mas o erro final pareceria ser apenas o payment-service.

Agora o fluxo e:

`	ext
provider falhou
  -> processed_events vira FAILED
  -> tenta paymentServiceClient.fail(paymentId)
  -> se essa notificacao tambem falhar:
       - incrementa metrica especifica
       - adiciona essa exception como suppressed na exception original
  -> relanca a exception original
`

A metrica adicionada foi:

`	ext
highpay_processor_payment_fail_notification_failed_total
`

Decisao importante:

- A causa principal continua sendo preservada.
- A falha secundaria nao some; ela fica em exception.getSuppressed().
- O evento fica FAILED no Inbox, permitindo uma tentativa futura.
## 32. Separacao entre Decisao do Provider e Atualizacao Interna

O processor agora separa claramente duas fases:

`	ext
1. obter decisao do provider
2. aplicar essa decisao no payment-service
`

Isso evita um erro grave.

Antes, qualquer exception caia no mesmo catch e o processor tentava marcar o pagamento como FAILED. Isso era perigoso neste cenario:

`	ext
provider aprovou pagamento
  -> payment-service falhou ao receber /approve
  -> processor caia no catch
  -> processor tentava chamar /fail
`

Esse comportamento poderia transformar uma aprovacao real do provider em falha interna. Agora isso nao acontece.

Fluxo novo:

`	ext
provider falha antes de decidir
  -> Inbox marca evento FAILED
  -> processor tenta marcar pagamento como FAILED
  -> mensagem pode ser reentregue depois

provider retorna SUCCESS
  -> processor tenta /approve
  -> se /approve falhar, Inbox marca FAILED
  -> processor NAO chama /fail
  -> retry futuro tenta aplicar /approve novamente

provider retorna REJECTED
  -> processor tenta /reject
  -> se /reject falhar, Inbox marca FAILED
  -> processor NAO chama /fail
  -> retry futuro tenta aplicar /reject novamente

provider retorna erro funcional
  -> processor tenta /fail
  -> se /fail falhar, Inbox marca FAILED
`

Decisao importante:

Depois que o provider ja decidiu, a decisao externa vira a fonte de verdade daquele evento. Se a atualizacao interna falhar, o correto e repetir a atualizacao interna, nao trocar o resultado para FAILED.
## 33. Decisao do Provider Persistida no Inbox

O Inbox do processor agora tambem persiste a decisao retornada pelo provider.

Foram adicionadas as colunas:

```sql
ALTER TABLE processed_events
    ADD COLUMN provider_status VARCHAR(30),
    ADD COLUMN provider_transaction_id VARCHAR(100);
```

Isso corrige um problema importante no retry.

Antes, se acontecesse isto:

`	ext
provider retorna SUCCESS
  -> payment-service falha no /approve
  -> Inbox marca evento FAILED
  -> RabbitMQ reentrega mensagem
  -> processor chamava provider de novo
`

Isso nao era ideal, porque o provider ja tinha decidido. Chamar de novo poderia criar uma segunda tentativa no provedor externo.

Agora o fluxo e:

`	ext
processor chama provider
  -> provider responde SUCCESS/REJECTED/ERROR
  -> processor salva provider_status e provider_transaction_id no processed_events
  -> tenta aplicar a decisao no payment-service
  -> se a aplicacao interna falhar, Inbox fica FAILED com a decisao salva

retry futuro
  -> processor reserva o evento novamente
  -> encontra provider_status salvo
  -> NAO chama provider de novo
  -> reaplica /approve, /reject ou /fail no payment-service
`

Essa e a diferenca entre repetir a operacao externa e repetir somente a atualizacao interna.

Decisao arquitetural:

- Antes da decisao do provider, retry pode chamar provider.
- Depois da decisao do provider, retry nao chama provider novamente.
- A decisao externa fica persistida no Inbox para ser reaplicada com seguranca.
## 34. Transicoes Internas Idempotentes no Payment-Service

Depois que o Inbox passou a guardar a decisao do provider, apareceu outro detalhe importante: o payment-service tambem precisa aceitar repeticao segura nas rotas internas.

Exemplo do problema:

```text
processor chama provider
  -> provider retorna SUCCESS
  -> processor chama /internal/payments/{id}/approve
  -> payment-service grava APPROVED
  -> processor falha antes de marcar Inbox como PROCESSED
  -> RabbitMQ entrega a mensagem novamente
  -> processor reaplica a decisao salva no Inbox
```

Se o payment-service recusasse a segunda chamada de `/approve`, o evento ficaria preso em retry mesmo com o pagamento ja aprovado corretamente.

A regra nova no dominio `Payment` e:

```text
CREATED -> PROCESSING       permitido
PROCESSING -> PROCESSING    permitido, sem alterar estado
PROCESSING -> APPROVED      permitido
APPROVED -> APPROVED        permitido se provider_transaction_id for o mesmo
PROCESSING -> REJECTED      permitido
REJECTED -> REJECTED        permitido se provider_transaction_id for o mesmo
PROCESSING -> FAILED        permitido
FAILED -> FAILED            permitido, sem alterar estado
estado final -> decisao diferente  erro
```

Isso nao transforma qualquer repeticao em sucesso. A repeticao so e aceita quando ela confirma o mesmo resultado ja gravado.

Por que isso importa:

- RabbitMQ pode reentregar mensagens.
- Chamadas HTTP internas podem ter timeout mesmo depois do servidor ter gravado no banco.
- O processor pode falhar depois de atualizar o payment-service e antes de atualizar o Inbox.
- Em todos esses casos, o retry precisa conseguir terminar o fluxo sem chamar provider de novo e sem contradizer o estado final ja salvo.

A decisao final ficou assim:

- Idempotencia HTTP publica protege a criacao do pagamento pelo cliente.
- Outbox protege a publicacao do evento depois da criacao.
- Inbox protege o consumo do evento no processor.
- Decisao do provider persistida evita chamada duplicada ao provider.
- Transicao interna idempotente permite reaplicar a decisao no payment-service.

Essas cinco pecas juntas formam o caminho de confiabilidade do fluxo de pagamento.
## 35. Payload de Evento com JSON Estruturado

O evento `PaymentCreated` deixou de ser montado e lido como string manual com regex.

Antes havia dois pontos frageis:

```text
payment-service montava JSON concatenando string
processor lia campos com regex
```

Isso funcionava no caso feliz, mas era sensivel a detalhes de formato. JSON valido pode ter campos em outra ordem, espacos diferentes e escapes de string. Regex para JSON tende a quebrar nesses casos.

Agora o fluxo ficou assim:

```text
payment-service cria objeto PaymentCreatedPayload
  -> ObjectMapper serializa para JSON
  -> payload vai para outbox_events.payload
  -> OutboxPublisher publica no RabbitMQ
  -> processor recebe string JSON
  -> ObjectMapper converte para PaymentCreatedEvent
```

A decisao importante aqui e separar o contrato do evento do jeito como a string e escrita.

O contrato continua tendo os campos principais:

```json
{
  "eventId": "...",
  "paymentId": "...",
  "merchantId": "...",
  "amount": 100.00,
  "currency": "BRL",
  "paymentMethod": "PIX",
  "status": "CREATED",
  "createdAt": "..."
}
```

O processor usa apenas o que precisa para processar:

```text
eventId
paymentId
amount
currency
```

Os outros campos continuam no evento porque ajudam auditoria, debug e evolucao futura.

Tambem foi adicionada dependencia explicita de `jackson-databind` nos dois servicos. Fizemos isso porque os starters usados nesse projeto nao deixavam Jackson disponivel diretamente no classpath de compilacao.
## 36. Retry do Consumer e Dead-Letter Queue

O processor agora tem uma politica explicita para falha ao consumir evento do RabbitMQ.

Configuracao principal:

```properties
spring.rabbitmq.listener.simple.default-requeue-rejected=false
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=5
spring.rabbitmq.listener.simple.retry.initial-interval=1000ms
spring.rabbitmq.listener.simple.retry.multiplier=2
spring.rabbitmq.listener.simple.retry.max-interval=10000ms
```

Fluxo quando o processor falha:

```text
RabbitMQ entrega PaymentCreated
  -> processor tenta processar
  -> se lancar exception, Spring AMQP faz retry local
  -> depois de 5 tentativas sem sucesso, a mensagem e rejeitada
  -> default-requeue-rejected=false impede loop infinito imediato
  -> RabbitMQ envia para a DLQ configurada
```

Foram adicionados:

```text
exchange normal: highpay.payments.exchange
queue normal:    highpay.payment-created.queue
routing normal:  payment.created

DLX:             highpay.payments.dlx
DLQ:             highpay.payment-created.dlq
routing DLQ:     payment.created.dead-letter
```

A fila principal ganhou estes argumentos:

```text
x-dead-letter-exchange=highpay.payments.dlx
x-dead-letter-routing-key=payment.created.dead-letter
```

Por que isso importa:

- Sem DLQ, uma mensagem ruim pode ficar repetindo para sempre.
- Sem retry, uma falha temporaria poderia mandar mensagem para DLQ cedo demais.
- Com retry + DLQ, falhas temporarias recebem novas tentativas e falhas persistentes ficam isoladas para inspecao manual.

Ponto operacional importante:

RabbitMQ nao permite mudar argumentos de uma fila existente. Se `highpay.payment-created.queue` ja existir localmente sem `x-dead-letter-exchange`, sera necessario apagar/recriar a fila ou recriar o ambiente Docker para a nova declaracao ser aceita.

Isso nao e migration de banco; e mudanca de topologia do broker.
## 37. Validacao do Provider Transaction Id

As rotas internas `/approve` e `/reject` recebem `providerTransactionId` do processor.

Esse valor representa a referencia da transacao no provider externo. Por isso ele nao pode ser vazio.

A validacao ficou em dois niveis:

```text
REST request
  -> ProviderTransactionRequest usa @NotBlank
  -> request invalida retorna 400 Bad Request

Dominio Payment
  -> approve(providerTransactionId) valida novamente
  -> reject(providerTransactionId) valida novamente
  -> chamada direta por outro adapter tambem fica protegida
```

Por que validar tambem no dominio:

- Controller nao deve ser a unica barreira de consistencia.
- Use cases podem ser chamados por testes, jobs, consumers ou outros adapters no futuro.
- O banco permite `provider_transaction_id` nulo para pagamentos ainda nao finalizados, mas nao queremos salvar final `APPROVED` ou `REJECTED` sem referencia do provider.

Fluxo invalido:

```text
processor chama /internal/payments/{id}/approve com providerTransactionId vazio
  -> payment-service retorna 400
  -> processor trata como falha de processamento
  -> mensagem pode seguir politica de retry/DLQ
```

Cobertura adicionada:

- Teste de dominio para `approve` sem provider transaction id.
- Teste de dominio para `reject` sem provider transaction id.
- Teste REST interno para `/approve` com campo vazio.
- Teste REST interno para `/reject` com campo vazio.
## 38. Clientes HTTP com JSON Estruturado

Os clientes HTTP do processor tambem deixaram de montar e ler JSON manualmente.

Antes:

```text
HttpProviderClient
  -> montava body JSON concatenando string
  -> lia resposta do provider com regex

HttpPaymentServiceClient
  -> montava body JSON concatenando string
```

Agora:

```text
HttpProviderClient
  -> cria ProviderPaymentRequest
  -> ObjectMapper serializa o request
  -> ObjectMapper desserializa ProviderPaymentResponse

HttpPaymentServiceClient
  -> cria ProviderTransactionRequest
  -> ObjectMapper serializa o request
```

Por que isso importa:

- JSON valido pode mudar ordem dos campos.
- Resposta pode ter espacos e campos extras.
- Strings podem precisar de escape.
- Regex para JSON nao e um contrato confiavel.

Cobertura adicionada:

- Teste do `HttpProviderClient` com servidor HTTP local, validando body enviado e parse da resposta em ordem diferente.
- Teste do `HttpProviderClient` para HTTP 500 virando resultado `ERROR`.
- Teste do `HttpPaymentServiceClient` validando body JSON enviado para `/approve`.
- Teste do `HttpPaymentServiceClient` validando erro quando payment-service retorna HTTP nao 2xx.
## 39. Timeout nas Chamadas Internas ao Payment-Service

O processor agora tambem tem timeout configuravel nas chamadas HTTP feitas para o payment-service.

Configuracao:

```properties
highpay.payment-service.request-timeout-ms=3000
```

Antes, o `HttpProviderClient` ja tinha timeout, mas o `HttpPaymentServiceClient` nao tinha timeout explicito por request.

Por que isso importa:

```text
processor consome mensagem do RabbitMQ
  -> chama payment-service
  -> payment-service trava ou demora demais
  -> sem timeout, a thread do consumer pode ficar presa
  -> fila atrasa e retry/DLQ demoram a agir
```

Com timeout:

```text
processor chama payment-service
  -> chamada ultrapassa request-timeout-ms
  -> client lanca IllegalStateException
  -> use case marca Inbox como FAILED quando aplicavel
  -> Spring AMQP aplica retry
  -> se continuar falhando, mensagem vai para DLQ
```

Isso fecha a mesma ideia aplicada ao provider: chamadas externas ou entre servicos precisam ter limite de tempo.

Cobertura adicionada:

- Teste do `HttpPaymentServiceClient` simulando timeout com servidor HTTP local lento.
## 40. Validacao Final do Fluxo Assincrono

O E2E final mostrou dois ajustes importantes:

1. O `payment-processor` precisa subir uma aplicacao web para expor `/actuator/health` na porta `8082`.
2. O `payment-service` precisa manter o Outbox Publisher ligado por padrao, senao o evento fica parado como `PENDING`.

Configuracao final do payment-service:

```properties
highpay.outbox.publisher.enabled=true
```

Fluxo validado no ambiente local:

```text
POST /api/v1/payments
  -> payment CREATED
  -> outbox_events PENDING
  -> OutboxPublisher publica no RabbitMQ
  -> outbox_events PUBLISHED
  -> payment-processor consome mensagem
  -> processed_events PROCESSED
  -> payment APPROVED
  -> filas RabbitMQ vazias
  -> DLQ vazia
```

Tambem foi corrigido o publisher para persistir explicitamente o status do evento depois da tentativa de publicacao:

```text
publish OK
  -> markAsPublished()
  -> save(event)

publish falhou
  -> markAsFailed(maxRetryAttempts)
  -> save(event)
```

Isso evita que uma publicacao bem-sucedida fique parecendo pendente no banco.

## 41. Tolerancia e Validacao do Payload Consumido

O evento `PaymentCreated` publicado pelo payment-service contem mais campos do que o processor precisa para decidir o pagamento.

O processor agora desserializa o JSON com Jackson ignorando campos desconhecidos, mas valida os campos obrigatorios antes de gravar Inbox:

- `eventId`
- `paymentId`
- `amount`
- `currency`

Por que isso importa:

```text
mensagem antiga ou invalida chega do RabbitMQ
  -> processor valida contrato minimo
  -> payload sem eventId falha como erro de contrato
  -> nao tenta gravar processed_events com id nulo
```

Isso troca uma falha tecnica confusa de banco por uma falha clara de payload invalido.

## 42. Correlation ID e Logs Estruturados

O fluxo agora propaga um identificador de correlacao entre os tres servicos.

Header padrao:

```text
X-Correlation-Id
```

Fluxo HTTP de entrada:

```text
cliente chama payment-service
  -> se X-Correlation-Id veio no request, o servico usa esse valor
  -> se nao veio, o servico gera um UUID
  -> o valor fica no MDC como correlationId
  -> o response devolve X-Correlation-Id
```

Como o Outbox publica o evento depois da request original, o MDC da request nao existe mais no momento do publish. Por isso o `payment-service` tambem grava `correlationId` dentro do payload `PaymentCreated`.

Fluxo assincrono:

```text
payment-service cria PaymentCreated com correlationId
  -> OutboxPublisher le o correlationId do payload
  -> publica no RabbitMQ com header X-Correlation-Id
  -> payment-processor consome a mensagem
  -> coloca correlationId no MDC
  -> propaga o header nas chamadas HTTP para payment-service e provider-simulator
```

Logs estruturados:

```text
timestamp=... level=INFO service=payment-service correlationId=... thread=... logger=... message="..."
```

Essa decisao melhora debug operacional porque uma unica tentativa de pagamento pode ser seguida nos logs do `payment-service`, do `payment-processor` e do `provider-simulator`, mesmo passando por RabbitMQ e chamadas HTTP internas.

## 43. Hardening Operacional

O `OutboxPublisher` passou a buscar eventos pendentes com `FOR UPDATE SKIP LOCKED`.

Isso permite rodar mais de uma instancia do `payment-service` sem que duas instancias selecionem e publiquem o mesmo registro de outbox ao mesmo tempo. A transacao bloqueia somente as linhas selecionadas; outras instancias pulam essas linhas e tentam os proximos eventos pendentes.

Foi adicionado reprocessamento manual de DLQ:

```text
POST /internal/rabbitmq/payment-created-dlq/requeue-one
X-Internal-Service-Token: <shared-secret>
```

Esse endpoint consome uma mensagem de `highpay.payment-created.dlq` e republica na exchange normal `highpay.payments.exchange` com routing key `payment.created`.

O `payment-processor` ganhou circuit breaker simples nas chamadas para:

```text
payment-service
provider-simulator
```

Configuracoes:

```properties
highpay.payment-service.circuit-breaker.failure-threshold=3
highpay.payment-service.circuit-breaker.open-duration-ms=10000
highpay.provider.circuit-breaker.failure-threshold=3
highpay.provider.circuit-breaker.open-duration-ms=10000
```

Quando o limite de falhas consecutivas e atingido, chamadas novas falham rapido durante a janela configurada. Isso evita manter threads do consumer presas tentando integrar com uma dependencia claramente indisponivel.

O segredo interno deixou de ter fallback no `application.properties`:

```properties
highpay.internal-auth.token=${HIGHPAY_INTERNAL_AUTH_TOKEN}
highpay.payment-service.internal-auth-token=${HIGHPAY_INTERNAL_AUTH_TOKEN}
```

Em testes, os modulos usam `src/test/resources/application.properties`. Em execucao local/compose, o valor deve vir do `.env`.

O `compose.yaml` agora consegue subir a stack completa:

```powershell
docker compose up -d --build
```

Tambem foi adicionado um script E2E local:

```powershell
.\scripts\e2e-local.ps1
```

Ele sobe a stack, espera os health checks HTTP, cria um pagamento e aguarda o status final `APPROVED`.
