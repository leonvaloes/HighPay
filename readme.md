# HighPay

HighPay e uma plataforma ficticia de processamento de pagamentos PIX criada para estudar arquitetura de sistemas financeiros com Java, Spring Boot, PostgreSQL e RabbitMQ.

O foco do projeto nao e CRUD. O foco e mostrar decisoes de confiabilidade: idempotencia, processamento assincrono, Outbox, Inbox, retry, DLQ, observabilidade e separacao de responsabilidades.

## Servicos

### payment-service

API principal de pagamentos.

Responsabilidades:

- criar pagamentos com `Idempotency-Key`;
- consultar pagamento por id;
- listar pagamentos paginados;
- persistir `Payment` no PostgreSQL;
- gravar evento `PaymentCreated` na tabela `outbox_events` na mesma transacao da criacao;
- publicar eventos pendentes no RabbitMQ via Outbox Publisher;
- expor endpoints internos para o processor atualizar status;
- expor health checks e metricas via Actuator.

Porta padrao: `8081`.

### payment-processor

Worker responsavel por consumir eventos `PaymentCreated`.

Responsabilidades:

- consumir mensagens do RabbitMQ;
- deduplicar eventos usando `processed_events`;
- chamar o provider externo/simulado;
- persistir a decisao do provider no Inbox;
- atualizar o status do pagamento no payment-service;
- aplicar retry e enviar falhas persistentes para DLQ;
- expor health checks e metricas via Actuator.

Porta padrao: `8082`.

### provider-simulator

Simulador de adquirente/provider externo.

Responsabilidades:

- receber solicitacao de processamento;
- responder cenarios `SUCCESS`, `REJECTED`, `ERROR`, `SLOW` e `TIMEOUT`;
- permitir testar falhas e timeouts sem depender de integracao real.

Porta padrao: `8083`.

## Fluxo Principal

```text
Cliente
  -> POST /api/v1/payments com Idempotency-Key
  -> payment-service cria Payment CREATED
  -> payment-service grava outbox_events PENDING na mesma transacao
  -> OutboxPublisher publica PaymentCreated no RabbitMQ
  -> payment-processor consome evento
  -> processor reserva eventId em processed_events
  -> processor marca pagamento como PROCESSING
  -> processor chama provider-simulator
  -> processor salva decisao do provider no Inbox
  -> processor chama /approve, /reject ou /fail no payment-service
  -> processor marca evento como PROCESSED
```

## Endpoints Publicos

```http
POST /api/v1/payments
Idempotency-Key: <uuid-ou-chave-unica>
Content-Type: application/json

{
  "merchantId": "merchant-001",
  "amount": 100.00,
  "currency": "BRL",
  "paymentMethod": "PIX"
}
```

```http
GET /api/v1/payments/{id}
```

```http
GET /api/v1/payments?page=0&size=20
```

## Endpoints Internos

Usados pelo `payment-processor` para atualizar o pagamento no `payment-service`.

```http
X-Internal-Service-Token: <shared-secret>

POST /internal/payments/{id}/processing
POST /internal/payments/{id}/approve
POST /internal/payments/{id}/reject
POST /internal/payments/{id}/fail
POST /internal/rabbitmq/payment-created-dlq/requeue-one
```

Esses endpoints aceitam apenas chamadas com o token interno correto. O `payment-service` valida `highpay.internal-auth.token` e o `payment-processor` envia `highpay.payment-service.internal-auth-token`; localmente ambos podem ser definidos pela variavel `HIGHPAY_INTERNAL_AUTH_TOKEN`.

`/approve` e `/reject` exigem body:

```json
{
  "providerTransactionId": "provider-123"
}
```

## Banco de Dados

O `payment-service` usa:

- `payments`
- `outbox_events`
- `flyway_schema_history`

O `payment-processor` usa:

- `processed_events`
- `flyway_schema_history_processor`

Ambos apontam para o mesmo PostgreSQL local por enquanto, mas com historicos Flyway separados.

## RabbitMQ

Topologia principal:

```text
exchange: highpay.payments.exchange
routing key: payment.created
queue: highpay.payment-created.queue
```

Dead-letter:

```text
DLX: highpay.payments.dlx
DLQ: highpay.payment-created.dlq
routing key: payment.created.dead-letter
```

Se a fila `highpay.payment-created.queue` ja existir sem DLQ no RabbitMQ local, recrie a fila ou o ambiente Docker. RabbitMQ nao aceita mudar argumentos de fila existente com declaracao diferente.

Para reprocessar uma mensagem da DLQ manualmente:

```http
POST /internal/rabbitmq/payment-created-dlq/requeue-one
X-Internal-Service-Token: <shared-secret>
```

O endpoint consome uma mensagem de `highpay.payment-created.dlq` e republica na exchange normal com routing key `payment.created`.

## Observabilidade

Actuator exposto:

```text
/actuator/health
/actuator/metrics
```

Metricas principais:

```text
highpay_payment_created_total
highpay_payment_idempotency_hit_total
highpay_payment_processing_started_total
highpay_payment_approved_total
highpay_payment_rejected_total
highpay_payment_failed_total
highpay_outbox_event_pending
highpay_outbox_event_published_total
highpay_outbox_event_failed_total
highpay_processor_payment_created_event_consumed_total
highpay_processor_duplicate_event_skipped_total
highpay_processor_provider_approved_total
highpay_processor_provider_rejected_total
highpay_processor_provider_failed_total
highpay_processor_processing_failed_total
highpay_processor_payment_fail_notification_failed_total
```

Logs:

- todos os servicos usam logs em formato `key=value` com `timestamp`, `level`, `service`, `correlationId`, `thread`, `logger` e `message`;
- requests HTTP aceitam `X-Correlation-Id`; se o header nao vier, o servico gera um UUID e devolve o valor no response;
- o `payment-service` salva `correlationId` no payload `PaymentCreated` e tambem publica esse valor como header no RabbitMQ;
- o `payment-processor` recupera o correlation id da mensagem, propaga para chamadas HTTP ao `payment-service` e ao `provider-simulator`, e mantem o valor no MDC dos logs.

## Como Rodar Localmente

1. Crie `.env` a partir do exemplo:

```powershell
Copy-Item env.example .env
```

2. Suba a stack completa:

```powershell
docker compose up -d --build
```

Tambem e possivel rodar apenas a infraestrutura e iniciar os servicos pela IDE/Maven:

```powershell
docker compose up -d postgres rabbitmq
```

3. Rode o provider simulator:

```powershell
cd backend/provider-simulator
.\mvnw.cmd spring-boot:run
```

4. Rode o payment-service:

```powershell
cd backend/payment-service
.\mvnw.cmd spring-boot:run
```

5. Rode o payment-processor:

```powershell
cd backend/payment-processor
.\mvnw.cmd spring-boot:run
```

## Como Testar

```powershell
cd backend/payment-service
.\mvnw.cmd test
```

```powershell
cd backend/payment-processor
.\mvnw.cmd test
```

```powershell
cd backend/provider-simulator
.\mvnw.cmd test
```

Teste ponta a ponta local:

```powershell
.\scripts\e2e-local.ps1
```

## Documentacao Detalhada

A explicacao longa dos fluxos e decisoes arquiteturais esta em:

- `docs/architecture-flows.md`

Esse arquivo explica passo a passo idempotencia HTTP, Outbox, RabbitMQ, Inbox, DLQ, observabilidade, retries e os motivos das decisoes tomadas.
