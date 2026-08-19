# HighPay - Explicacao Completa Para Continuar Em Outro Prompt

Este arquivo foi criado para servir como memoria completa do que fizemos juntos no projeto HighPay.

A ideia e que voce consiga abrir outro prompt, mandar este arquivo junto com o codigo, e continuar exatamente do ponto onde paramos, sem precisar reconstruir todo o raciocinio.

Observacao importante: eu nao consigo transcrever literalmente cada token interno de raciocinio privado do modelo, mas este documento registra com detalhes o raciocinio de engenharia, as decisoes tomadas, as duvidas que apareceram, as respostas conceituais e o estado final do projeto.

## 1. Contexto Da Preparacao

Voce esta usando o projeto HighPay para se preparar para uma vaga mais forte de backend, com foco em arquitetura e sistemas financeiros.

O projeto deixou de ser apenas um CRUD de pagamentos e virou uma simulacao de plataforma de pagamentos com preocupacoes reais:

- idempotencia HTTP;
- consistencia transacional;
- arquitetura em camadas;
- Ports & Adapters / Hexagonal Architecture;
- PostgreSQL;
- RabbitMQ;
- Transactional Outbox;
- Inbox no consumidor;
- retry;
- DLQ;
- observabilidade com Actuator e metricas;
- separacao entre API publica, worker assincrono e provider externo;
- testes unitarios, integracao e E2E local.

A vaga que voce esta se preparando, pelo tipo de assunto que decidimos trabalhar, parece exigir capacidade de conversar sobre sistemas distribuidos, mensageria, confiabilidade, pagamentos e desenho de arquitetura. Entao o projeto foi sendo guiado para gerar argumentos de entrevista, nao apenas codigo funcionando.

Em uma entrevista, a historia principal que voce pode contar e:

```text
Eu construi um fluxo de pagamento com criacao idempotente, persistencia no PostgreSQL, publicacao confiavel de eventos com Transactional Outbox, processamento assincrono via RabbitMQ, deduplicacao do lado consumidor com Inbox, retry/DLQ, provider externo simulado e observabilidade basica.
```

Essa frase resume bem o projeto.

## 2. Como A Conversa Comecou

Voce pediu primeiro para ler o `readme.md` e entender o status atual do projeto.

Depois voce pediu:

```text
veja o status atual do projeto e me fale o que devemos fazer juntos
me explique a arquitetura do projeto as decisoes tomadas
```

A partir dai, fomos explicando o projeto de baixo para cima, porque havia varios conceitos novos ou nebulosos:

- o que era uma porta;
- o que era um adapter;
- quem implementa quem;
- por que controller chama use case;
- por que use case depende de interface;
- por que JPA fica na infraestrutura;
- por que o dominio nao deve saber de banco ou RabbitMQ.

Quando voce dizia `daqui para baixo nao entendi` ou `ainda nao entendi`, a abordagem foi pausar a implementacao e explicar os conceitos em linguagem direta, usando o proprio projeto como exemplo.

## 3. Ideia Principal Da Arquitetura

O projeto foi organizado em camadas:

```text
domain
  regras de negocio puras

application
  casos de uso e portas

interfaces
  REST/controllers/requests/responses

infrastructure
  banco, RabbitMQ, HTTP clients, observabilidade, detalhes externos
```

A decisao central foi separar regra de negocio de tecnologia.

### 3.1 Domain

O dominio tem a entidade `Payment` e enums como `PaymentStatus` e `PaymentMethod`.

O dominio responde perguntas como:

```text
Um pagamento pode sair de CREATED para APPROVED direto? Nao.
Um pagamento APPROVED pode ser aprovado de novo? Sim, se for a mesma decisao do provider.
Um pagamento APPROVED pode virar REJECTED? Nao.
```

Ou seja, regra de status fica no dominio, nao no controller.

### 3.2 Application

A camada application tem os use cases:

- `CreatePaymentUseCase`;
- `GetPaymentUseCase`;
- `ListPaymentsUseCase`;
- `MarkPaymentAsProcessingUseCase`;
- `ApprovePaymentUseCase`;
- `RejectPaymentUseCase`;
- `FailPaymentUseCase`.

Tambem tem as portas:

- `PaymentRepository`;
- `OutboxEventRepository`;
- `PaymentMetrics`;
- `OutboxMetrics`.

Porta e contrato. Ela diz o que a aplicacao precisa, sem dizer como isso e implementado.

O use case nao sabe se isso usa PostgreSQL, MongoDB, memoria, arquivo ou API externa.

### 3.3 Infrastructure

Infrastructure implementa os contratos da application.

Exemplo:

```text
PaymentRepositoryAdapter implements PaymentRepository
OutboxEventRepositoryAdapter implements OutboxEventRepository
```

Foi aqui que voce perguntou: `ENT O ADAPTER E A IMPLEMENTACAO DA PORTA?`

A resposta foi: sim.

A porta e a interface/contrato. O adapter e a implementacao concreta daquele contrato usando alguma tecnologia.

Exemplo simples:

```text
Porta: PaymentRepository
  -> contrato que a aplicacao usa

Adapter: PaymentRepositoryAdapter
  -> implementa esse contrato usando Spring Data JPA
```

### 3.4 Interfaces REST

A camada `interfaces` contem entrada HTTP:

- `PaymentController`;
- `InternalPaymentController`;
- requests;
- responses;
- `GlobalExceptionHandler`.

A funcao do controller e traduzir HTTP para caso de uso. Ele nao deve conter regra importante de negocio.

## 4. Sobre A Porta E A Funcao Da Porta

Voce perguntou: `UE QUAL A FUNCAO DA PORTA`.

A porta existe para inverter a dependencia.

Sem porta:

```text
CreatePaymentUseCase -> JpaPaymentRepository -> Spring Data -> PostgreSQL
```

Isso acopla regra de aplicacao a tecnologia.

Com porta:

```text
CreatePaymentUseCase -> PaymentRepository (porta)
PaymentRepositoryAdapter -> JpaPaymentRepository -> PostgreSQL
```

O use case depende de um contrato estavel. Isso facilita trocar persistencia, testar use case sem banco real, manter regra longe de framework e explicar arquitetura em entrevista.

Frase boa para entrevista:

```text
Eu usei portas para que a camada de aplicacao dependesse de contratos do negocio, e nao de detalhes de infraestrutura como JPA, RabbitMQ ou HTTP clients.
```

## 5. O Que Foi Implementado No Payment-Service

O `payment-service` virou a API principal de pagamentos.

Porta padrao: `8081`.

Responsabilidades:

- criar pagamento;
- consultar por ID;
- listar pagamentos;
- garantir idempotencia HTTP;
- persistir no PostgreSQL;
- gravar evento no Outbox;
- publicar evento no RabbitMQ;
- expor endpoints internos para o processor atualizar status;
- expor Actuator e metricas.

## 6. Endpoints Publicos Criados

### 6.1 Criar pagamento

```http
POST /api/v1/payments
Idempotency-Key: <chave-unica>
Content-Type: application/json

{
  "merchantId": "merchant-001",
  "amount": 100.00,
  "currency": "BRL",
  "paymentMethod": "PIX"
}
```

### 6.2 Buscar pagamento por ID

```http
GET /api/v1/payments/{id}
```

### 6.3 Listar pagamentos

Voce pediu: `faca a listagem antes`.

Entao implementamos:

```http
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

A camada application nao retorna `Page` do Spring Data diretamente. Ela usa `ListPaymentsResult` para nao acoplar application ao Spring Data.

## 7. Retorno Padronizado

Voce perguntou se foi feita padronizacao do retorno. Sim.

O retorno de erro foi padronizado com `GlobalExceptionHandler` usando `@RestControllerAdvice`.

Formato:

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

Voce perguntou como isso vale para toda request. A resposta: `@RestControllerAdvice` intercepta excecoes lancadas pelos controllers e transforma em resposta HTTP padronizada.

Tratamentos:

- validacao de request -> 400;
- header ausente -> 400;
- UUID invalido -> 400;
- pagamento nao encontrado -> 404;
- conflito de idempotencia -> 409;
- erro inesperado -> 500.

## 8. Idempotencia HTTP

Voce perguntou: `oque e mesmo idempotencia HTTP`.

Uma operacao idempotente pode ser repetida sem gerar efeito duplicado.

No projeto:

```text
Cliente manda POST /payments com Idempotency-Key ABC
payment-service cria pagamento #1

Cliente repete o mesmo POST com a mesma Idempotency-Key ABC
payment-service retorna o pagamento #1
nao cria pagamento #2
```

Isso e essencial em pagamentos porque clientes podem repetir request por timeout, queda de rede ou retry automatico.

## 9. Quem Gera O Idempotency-Key

Voce perguntou: `Mas quem vai gerar esse Idempotency-Key`.

A resposta correta: quem gera o Idempotency-Key e o cliente que esta iniciando a operacao.

Por que? Porque a chave identifica a tentativa do cliente.

Se o backend gerasse uma chave diferente em cada request, ele nao conseguiria saber que duas requests representam a mesma tentativa.

Exemplo:

```text
App/cliente cria chave: checkout-123-tentativa-1
POST /payments com essa chave
se der timeout, repete a mesma chave
backend reconhece e nao duplica pagamento
```

## 10. Fingerprint Da Request

Depois evoluimos a idempotencia.

Problema:

```text
Request 1: Idempotency-Key ABC, amount 100
Request 2: Idempotency-Key ABC, amount 999
```

Se o sistema so olhasse a chave, retornaria o pagamento antigo, mascarando erro do cliente.

Entao adicionamos `request_fingerprint`.

O fingerprint e um SHA-256 calculado a partir da intencao da request:

```text
merchantId | amount | currency | paymentMethod
```

Fluxo:

```text
se chave nao existe:
  cria pagamento
  salva fingerprint

se chave existe e fingerprint igual:
  retorna pagamento existente

se chave existe e fingerprint diferente:
  retorna 409 Conflict
```

Isso torna a idempotencia mais correta.
## 11. O Que E Outbox

Voce disse: `mas oque e esse Outbox`.

Outbox e uma tabela no banco usada para guardar a intencao de publicar um evento.

Problema sem Outbox:

```text
payment-service salva Payment no banco
payment-service tenta publicar evento no RabbitMQ
aplicacao cai antes de publicar
```

Resultado ruim:

```text
Payment existe, mas ninguem foi avisado.
```

Com Outbox:

```text
BEGIN
INSERT payments
INSERT outbox_events
COMMIT
```

Se a aplicacao cair depois do commit, o evento ainda esta salvo em `outbox_events` como `PENDING`.

Depois um publisher busca eventos pendentes e publica no RabbitMQ.

## 12. Sua Duvida Sobre Uma Tabela De Status

Voce perguntou:

```text
e pq n pd salvar em uma tabela fazendo o controle somente do status?
Tipo se ta created ele tem q processar tendeu?
```

Essa foi uma duvida muito boa.

Poder ate poderia usar a tabela `payments` como fonte para procurar pagamentos `CREATED`. Mas isso mistura duas responsabilidades:

1. estado de negocio do pagamento;
2. intencao de comunicacao/evento para outro sistema.

O status `CREATED` diz o estado do pagamento.

O outbox diz:

```text
preciso publicar o evento PaymentCreated para o mundo externo/processador
```

Separar isso e importante porque uma mesma mudanca de negocio pode gerar varias comunicacoes no futuro:

- `PaymentCreated` para processor;
- `PaymentApproved` para antifraude;
- `PaymentRejected` para notificacao;
- `PaymentFailed` para conciliacao;
- `RefundRequested` para outro fluxo.

Se tudo dependesse apenas do status do pagamento, ficaria dificil controlar retry, quantidade de tentativas, payload exato, ordem e destino.

## 13. Cada Intencao De Comunicacao Vira Um Registro

Voce perguntou:

```text
ent oque vc ta me falando q para cada intencao de comunicacao sera um registro ?
```

Sim. Essa e a essencia do Outbox.

Cada evento que precisa sair do servico vira uma linha.

Exemplo:

```text
Pagamento criado
  -> outbox_events: PaymentCreated

Pagamento aprovado
  -> outbox_events: PaymentApproved

Pagamento estornado
  -> outbox_events: PaymentRefunded
```

A tabela nao e apenas controle de status. Ela e um log confiavel de eventos que precisam ser publicados.

## 14. Tabela outbox_events

Criamos migration:

```text
V2__create_outbox_events_table.sql
```

Campos principais:

```text
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

Estados:

```text
PENDING
PUBLISHED
FAILED
```

Fluxo:

```text
Pagamento criado
  -> outbox_events PENDING

Publisher publicou no RabbitMQ
  -> outbox_events PUBLISHED

Publisher falhou
  -> retry_count aumenta
  -> se chegar no limite, FAILED
```

## 15. OutboxPublisher

Criamos `OutboxPublisher` no `payment-service`.

Ele:

- roda agendado;
- busca eventos `PENDING`;
- publica no RabbitMQ;
- marca como `PUBLISHED`;
- em erro, incrementa retry e pode marcar `FAILED`.

Configuracoes:

```properties
highpay.outbox.publisher.enabled=true
highpay.outbox.publisher.fixed-delay-ms=5000
highpay.outbox.publisher.batch-size=20
highpay.outbox.publisher.max-retry-attempts=5
```

Um bug apareceu no E2E final:

```text
O pagamento era processado, mas outbox_events continuava PENDING.
```

Corrigimos o publisher para salvar explicitamente a entidade depois de mudar status:

```text
publish OK
  -> markAsPublished()
  -> save(event)

publish falhou
  -> markAsFailed(maxRetryAttempts)
  -> save(event)
```

Tambem percebemos que o publisher estava desligado por padrao:

```properties
highpay.outbox.publisher.enabled=false
```

Isso fazia o evento ficar parado.

Mudamos para:

```properties
highpay.outbox.publisher.enabled=true
```

## 16. RabbitMQ

Topologia criada:

```text
exchange: highpay.payments.exchange
routing key: payment.created
queue: highpay.payment-created.queue
```

DLQ:

```text
DLX: highpay.payments.dlx
DLQ: highpay.payment-created.dlq
routing key: payment.created.dead-letter
```

A fila principal tem argumentos:

```text
x-dead-letter-exchange=highpay.payments.dlx
x-dead-letter-routing-key=payment.created.dead-letter
```

Problema encontrado:

RabbitMQ nao aceita mudar argumentos de uma fila ja existente.

Durante o E2E, a fila antiga existia sem DLQ e gerou erro `PRECONDITION_FAILED`.

Resolucao local:

```powershell
docker compose exec rabbitmq rabbitmqctl delete_queue highpay.payment-created.queue
```

Depois a aplicacao recriou a fila com os argumentos corretos.

## 17. Payment-Processor

Criamos um novo servico:

```text
backend/payment-processor
```

Porta: `8082`.

Responsabilidade:

- consumir `PaymentCreated` do RabbitMQ;
- garantir que o evento nao seja processado duplicado;
- chamar provider externo/simulado;
- atualizar payment-service;
- aplicar retry/DLQ;
- expor health/metrics.

## 18. Por Que O Processor Nao Atualiza O Banco Do Payment-Service Direto

Decisao importante:

```text
O payment-service e dono do agregado Payment.
```

Entao o processor nao faz `UPDATE payments SET status = ...` diretamente.

Ele chama endpoints internos:

```http
POST /internal/payments/{id}/processing
POST /internal/payments/{id}/approve
POST /internal/payments/{id}/reject
POST /internal/payments/{id}/fail
```

Motivo:

- regras de transicao ficam no dominio do payment-service;
- processor nao viola invariantes;
- schema interno do payment-service nao vira contrato do processor;
- fica mais alinhado com arquitetura de microservicos.

## 19. Endpoints Internos

Implementamos no `InternalPaymentController`.

Rotas:

```text
POST /internal/payments/{id}/processing
POST /internal/payments/{id}/approve
POST /internal/payments/{id}/reject
POST /internal/payments/{id}/fail
```

`approve` e `reject` exigem:

```json
{
  "providerTransactionId": "provider-123"
}
```

Validamos `providerTransactionId` em dois lugares:

- request REST com `@NotBlank`;
- dominio `Payment.approve/reject`.

Isso evita pagamento finalizado sem referencia externa.

## 20. Provider-Simulator

Criamos outro servico:

```text
backend/provider-simulator
```

Porta: `8083`.

Endpoint:

```http
POST /api/v1/provider/payments
```

Cenarios:

```text
SUCCESS
REJECTED
ERROR
SLOW
TIMEOUT
```

Isso permite simular adquirente/provider externo sem depender de um sistema real.

Em entrevista, voce pode dizer:

```text
Eu separei o provider em outro servico para simular uma integracao externa real, inclusive com cenarios de falha, lentidao e timeout.
```

## 21. Inbox / processed_events

Depois do Outbox, adicionamos Inbox no consumidor.

Tabela:

```text
processed_events
```

Estados:

```text
PROCESSING
PROCESSED
FAILED
```

Por que precisa?

RabbitMQ pode entregar a mesma mensagem mais de uma vez.

Sem Inbox:

```text
mensagem PaymentCreated chega duas vezes
processor chama provider duas vezes
provider pode criar duas transacoes externas
```

Com Inbox:

```text
processor recebe evento
  -> tenta reservar eventId em processed_events
  -> se ja existe PROCESSING ou PROCESSED, ignora
  -> se FAILED, pode tentar de novo
```

Isso protege contra duplicidade no consumidor.

## 22. Decisao Do Provider Persistida No Inbox

Depois evoluimos o Inbox.

Problema:

```text
provider aprova
processor tenta chamar payment-service /approve
payment-service falha
RabbitMQ reentrega mensagem
processor chama provider de novo
```

Isso e perigoso.

Corrigimos persistindo no Inbox:

```text
provider_status
provider_transaction_id
```

Fluxo novo:

```text
provider decidiu SUCCESS
  -> salva SUCCESS + providerTransactionId no processed_events
  -> tenta aplicar /approve
  -> se falhar, retry futuro nao chama provider de novo
  -> retry futuro reaplica a decisao salva
```

Essa foi uma das decisoes mais importantes do projeto.

Frase boa para entrevista:

```text
Depois que o provider externo decide, eu persisto essa decisao no Inbox para que retries reapliquem a decisao internamente sem chamar o provider novamente.
```

## 23. Separacao Entre Decidir E Aplicar

Refatoramos o processor para separar duas fases:

```text
1. obter decisao do provider
2. aplicar decisao no payment-service
```

Antes, qualquer erro poderia cair num catch geral e tentar marcar pagamento como `FAILED`.

Isso seria perigoso:

```text
provider aprovou
payment-service falhou no /approve
processor chama /fail
```

Resultado ruim: um pagamento aprovado pelo provider poderia virar falha interna.

Agora:

```text
provider falhou antes de decidir
  -> tenta marcar FAILED

provider aprovou
  -> tenta APPROVE
  -> se falhar, retry reaplica APPROVE
  -> nao chama FAIL

provider rejeitou
  -> tenta REJECT
  -> se falhar, retry reaplica REJECT
  -> nao chama FAIL
```

Isso preserva a decisao externa.
## 24. Transicoes Internas Idempotentes

Depois que o Inbox passou a salvar a decisao, o payment-service precisou aceitar repeticoes seguras.

Exemplo:

```text
processor chama /approve
payment-service salva APPROVED
processor cai antes de marcar Inbox PROCESSED
RabbitMQ reentrega
processor chama /approve de novo
```

A segunda chamada precisa dar certo se for a mesma decisao.

Regras implementadas:

```text
CREATED -> PROCESSING permitido
PROCESSING -> PROCESSING permitido como no-op
PROCESSING -> APPROVED permitido
APPROVED -> APPROVED permitido se providerTransactionId igual
PROCESSING -> REJECTED permitido
REJECTED -> REJECTED permitido se providerTransactionId igual
PROCESSING -> FAILED permitido
FAILED -> FAILED permitido como no-op
estado final -> decisao diferente gera conflito
```

Isso fecha o ciclo de confiabilidade:

- idempotencia HTTP protege criacao;
- Outbox protege publicacao;
- Inbox protege consumo;
- decisao do provider persistida evita chamada externa duplicada;
- transicao interna idempotente permite retry seguro.

## 25. JSON Estruturado Em Vez De Regex

Inicialmente havia pontos com JSON montado ou lido manualmente.

Evoluimos para Jackson `ObjectMapper`.

No payment-service:

```text
PaymentCreatedPayload -> ObjectMapper -> JSON no outbox
```

No processor:

```text
payload JSON -> ObjectMapper -> PaymentCreatedEvent
```

Nos HTTP clients:

```text
request record -> ObjectMapper -> JSON
response JSON -> ObjectMapper -> response record
```

Motivo:

- JSON pode mudar ordem de campos;
- pode ter espacos;
- pode ter campos extras;
- string pode precisar de escape;
- regex para JSON e fragil.

## 26. Payload Do Evento

Evento publicado:

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

O processor usa apenas:

```text
eventId
paymentId
amount
currency
```

Campos extras sao ignorados com `@JsonIgnoreProperties(ignoreUnknown = true)`.

Tambem adicionamos validacao obrigatoria:

- `eventId` nao pode ser nulo;
- `paymentId` nao pode ser nulo;
- `amount` precisa existir e ser maior que zero;
- `currency` precisa existir e nao ser blank.

Isso corrigiu erro encontrado no E2E:

```text
mensagem antiga sem eventId tentava gravar processed_events com id nulo
```

Agora falha como contrato invalido antes de chegar no banco.

## 27. Retry E DLQ No Consumer

Configuracao no processor:

```properties
spring.rabbitmq.listener.simple.default-requeue-rejected=false
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=5
spring.rabbitmq.listener.simple.retry.initial-interval=1000ms
spring.rabbitmq.listener.simple.retry.multiplier=2
spring.rabbitmq.listener.simple.retry.max-interval=10000ms
```

Fluxo:

```text
mensagem chega
processor tenta processar
se falha, Spring AMQP faz retry local
se continuar falhando depois de 5 tentativas, mensagem e rejeitada
RabbitMQ manda para DLQ
```

Sem isso, mensagem ruim poderia gerar loop infinito.

## 28. Observabilidade

Voce perguntou: `ONDE ESTAMOS APLICANDO OBSERVABILIDADE?`

Aplicamos em tres niveis.

### 28.1 Health checks

Todos os servicos expoem:

```text
/actuator/health
```

Portas:

```text
payment-service: http://localhost:8081/actuator/health
payment-processor: http://localhost:8082/actuator/health
provider-simulator: http://localhost:8083/actuator/health
```

Durante o E2E descobrimos que o processor nao respondia health porque nao tinha starter web.

Corrigimos adicionando:

```xml
<artifactId>spring-boot-starter-webmvc</artifactId>
```

### 28.2 Metrics

Metricas adicionadas no payment-service:

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
```

Metricas adicionadas no processor:

```text
highpay_processor_payment_created_event_consumed_total
highpay_processor_duplicate_event_skipped_total
highpay_processor_provider_approved_total
highpay_processor_provider_rejected_total
highpay_processor_provider_failed_total
highpay_processor_processing_failed_total
highpay_processor_payment_fail_notification_failed_total
```

### 28.3 Estados persistidos

Tambem temos observabilidade operacional via banco e RabbitMQ:

```sql
SELECT status, count(*) FROM outbox_events GROUP BY status;
SELECT status, count(*) FROM processed_events GROUP BY status;
SELECT status, count(*) FROM payments GROUP BY status;
```

E via RabbitMQ:

```powershell
docker compose exec rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged
```

## 29. Timeouts

Adicionamos timeout nos HTTP clients.

Provider client:

```properties
highpay.provider.request-timeout-ms=3000
```

Payment-service client dentro do processor:

```properties
highpay.payment-service.request-timeout-ms=3000
```

Motivo:

Sem timeout, uma thread do consumer pode ficar presa esperando uma chamada HTTP que nunca responde.

Com timeout:

```text
chamada demora demais
client lanca exception
use case marca evento FAILED quando aplicavel
Spring AMQP aplica retry
se persistir, DLQ
```

## 30. Testes Criados E Validados

Rodamos testes de todos os servicos.

Resultado final registrado:

```text
payment-service: 36 testes verdes
payment-processor: 22 testes verdes
provider-simulator: 4 testes verdes
```

Coberturas importantes:

- criacao de pagamento;
- idempotencia;
- conflito de idempotencia por fingerprint diferente;
- busca por ID;
- listagem paginada;
- tratamento global de erro;
- transicoes de dominio;
- endpoints internos;
- providerTransactionId obrigatorio;
- OutboxPublisher sucesso/falha/retry;
- RabbitMQ config;
- processor consumindo evento;
- Inbox com duplicate skip;
- retry de evento FAILED;
- reutilizacao de decisao salva do provider;
- clients HTTP com JSON estruturado;
- timeout de client.

## 31. E2E Real Que Fizemos

Subimos:

```text
PostgreSQL via Docker
RabbitMQ via Docker
payment-service na 8081
payment-processor na 8082
provider-simulator na 8083
```

Health final:

```text
8081 /actuator/health -> UP
8082 /actuator/health -> UP
8083 /actuator/health -> UP
```

Criamos pagamento:

```text
POST /api/v1/payments
```

Resultado final:

```json
{
  "initialStatus": "CREATED",
  "finalStatus": "APPROVED",
  "providerTransactionId": "provider-..."
}
```

Checagem final do banco:

```text
outbox_events: PUBLISHED
processed_events: PROCESSED
payments: APPROVED
```

Checagem RabbitMQ:

```text
highpay.payment-created.queue: 0 ready, 0 unacknowledged
highpay.payment-created.dlq: 0 ready, 0 unacknowledged
```

Tambem validamos:

```text
repetir POST com mesma Idempotency-Key retorna o mesmo paymentId
```

## 32. Problemas Que Encontramos E Corrigimos

### 32.1 RabbitMQ PRECONDITION_FAILED

Causa: fila antiga existia sem argumentos DLQ.

RabbitMQ nao permite redeclarar a mesma fila com argumentos diferentes.

Correcao local:

```powershell
docker compose exec rabbitmq rabbitmqctl delete_queue highpay.payment-created.queue
```

### 32.2 Processor sem health na 8082

Causa: o processor tinha Actuator, mas nao tinha web stack.

Correcao:

```xml
spring-boot-starter-webmvc
```

### 32.3 Payload com campos extras quebrava Jackson

Causa: `PaymentCreatedEvent` nao ignorava campos desconhecidos.

Correcao:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
```

### 32.4 Mensagem antiga sem eventId quebrava banco

Causa: processor tentava gravar Inbox com ID nulo.

Correcao: validar payload antes de chamar repository.

### 32.5 Outbox ficava PENDING

Causa 1: Publisher desligado por padrao.

Correcao:

```properties
highpay.outbox.publisher.enabled=true
```

Causa 2: status alterado em memoria precisava ser salvo explicitamente.

Correcao:

```text
jpaOutboxEventRepository.save(event)
```

## 33. Estado Atual Dos Servicos

### payment-service

Local: `backend/payment-service`

Porta: `8081`

Principais pacotes:

```text
application
  exception
  port
  usecase

domain
  enums
  model

infrastructure
  messaging
  observability
  persistence

interfaces
  rest
```

### payment-processor

Local: `backend/payment-processor`

Porta: `8082`

Principais pacotes:

```text
application
  model
  port
  usecase

infrastructure
  client
  messaging
  observability
  persistence
```

### provider-simulator

Local: `backend/provider-simulator`

Porta: `8083`

## 34. Como Rodar O Projeto

Subir infra:

```powershell
docker compose up -d
```

Rodar provider:

```powershell
cd backend/provider-simulator
.\mvnw.cmd spring-boot:run
```

Rodar payment-service:

```powershell
cd backend/payment-service
.\mvnw.cmd spring-boot:run
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

Consultar:

```powershell
Invoke-RestMethod -Uri http://localhost:8081/api/v1/payments/{id}
```

Listar:

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/payments?page=0&size=10"
```

## 35. Como Rodar Testes

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
## 36. Como Explicar Na Entrevista

### Pergunta: por que idempotencia?

Resposta:

```text
Em pagamentos, o cliente pode repetir uma request por timeout ou instabilidade de rede. Sem idempotencia, poderiamos criar duas cobrancas para a mesma intencao. Por isso eu exijo Idempotency-Key e salvo um fingerprint do payload original. Repeticoes iguais retornam o mesmo pagamento; repeticoes com payload diferente retornam 409 Conflict.
```

### Pergunta: por que Outbox?

Resposta:

```text
Porque salvar no banco e publicar no RabbitMQ nao sao uma unica transacao atomica. O Outbox garante que a intencao de publicar o evento seja salva na mesma transacao do pagamento. Se a aplicacao cair depois do commit, o evento continua no banco como PENDING e sera publicado depois.
```

### Pergunta: por que Inbox?

Resposta:

```text
Porque RabbitMQ trabalha com entrega ao menos uma vez. A mesma mensagem pode chegar novamente. O Inbox registra eventId e status de processamento para evitar chamar o provider duplicado e para permitir retry controlado quando algo falha.
```

### Pergunta: por que persistir decisao do provider?

Resposta:

```text
Depois que o provider externo decide, nao quero chama-lo de novo em um retry, porque isso pode criar uma duplicidade externa. Entao salvo provider_status e provider_transaction_id no Inbox. Se falhar aplicar a decisao no payment-service, o retry reaplica a mesma decisao salva.
```

### Pergunta: por que processor nao atualiza tabela payments direto?

Resposta:

```text
Porque o payment-service e dono do agregado Payment. As regras de transicao de status ficam no dominio do payment-service. O processor chama endpoints internos para nao violar regra de dominio nem acoplar no schema interno do outro servico.
```

### Pergunta: onde esta observabilidade?

Resposta:

```text
Nos health checks de todos os servicos via Actuator, nas metricas customizadas de pagamentos/outbox/processor, nos estados persistidos em payments/outbox_events/processed_events e nas filas RabbitMQ/DLQ que permitem inspecionar backlog e falhas.
```

### Pergunta: o que acontece se RabbitMQ cair?

Resposta:

```text
O pagamento ainda e criado e o evento fica no outbox como PENDING. O publisher tenta publicar depois. Se falhar, incrementa retry e pode marcar FAILED quando atingir o limite. Isso evita perder o evento.
```

### Pergunta: o que acontece se provider aprovar mas payment-service falhar?

Resposta:

```text
O processor ja salvou a decisao do provider no Inbox. O evento fica FAILED para retry, mas o retry nao chama provider novamente. Ele reaplica /approve no payment-service usando o providerTransactionId salvo.
```

## 37. Pensamento De Engenharia Que Guiou As Decisoes

O objetivo nao foi inventar complexidade por complexidade.

O raciocinio foi:

```text
Pagamento e dominio sensivel.
Duplicidade custa caro.
Falha parcial e normal em sistema distribuido.
Mensageria entrega ao menos uma vez.
Banco e RabbitMQ nao compartilham transacao atomica.
Chamadas HTTP podem dar timeout depois de terem produzido efeito.
Entao precisamos desenhar para retry seguro.
```

Por isso as pecas apareceram nesta ordem:

1. criar pagamento corretamente;
2. proteger criacao com idempotencia;
3. separar camadas para manter regra clara;
4. adicionar consulta/listagem para operacao basica;
5. padronizar erros;
6. adicionar Outbox para nao perder evento;
7. adicionar RabbitMQ para assincronia;
8. criar processor para consumir;
9. adicionar provider-simulator para representar sistema externo;
10. adicionar Inbox para deduplicar consumo;
11. persistir decisao do provider para retry seguro;
12. tornar transicoes internas idempotentes;
13. adicionar retry/DLQ;
14. adicionar observabilidade;
15. validar com E2E real.

## 38. Pontos Que Ainda Podem Evoluir

Mesmo com o fluxo funcionando, ainda ha evolucoes boas para uma proxima etapa:

- autenticar endpoints internos;
- adicionar correlation id/request id nos logs;
- logs estruturados JSON;
- tracing distribuido com OpenTelemetry;
- Prometheus/Grafana local;
- lock ou `SKIP LOCKED` no Outbox para multiplas instancias;
- reprocessamento manual de DLQ;
- reprocessamento de outbox `FAILED`;
- circuit breaker para provider;
- contract tests entre payment-service e processor;
- Dockerfiles para cada servico;
- compose subindo os tres servicos alem de Postgres/RabbitMQ;
- perfis Spring (`local`, `test`, `prod`);
- seguranca de secrets;
- endpoint administrativo para consultar outbox/inbox;
- testes de concorrencia para idempotencia;
- testes E2E automatizados.

## 39. Estado Final Confirmado Antes Deste Arquivo

Antes de criar este documento, o estado confirmado foi:

```text
payment-service: testes passando
payment-processor: testes passando
provider-simulator: testes passando
E2E real: pagamento saiu de CREATED para APPROVED
Outbox: PUBLISHED
Inbox: PROCESSED
RabbitMQ queue: vazia
RabbitMQ DLQ: vazia
servicos Java locais: parados
logs temporarios: removidos
```

## 40. Arquivos Mais Importantes Para Continuar

Leia estes primeiro em outro prompt:

```text
readme.md
docs/architecture-flows.md
explicacao.md
```

Depois olhe:

```text
backend/payment-service/src/main/java/com/highpay/payment/domain/model/Payment.java
backend/payment-service/src/main/java/com/highpay/payment/application/usecase/CreatePaymentUseCase.java
backend/payment-service/src/main/java/com/highpay/payment/infrastructure/messaging/outbox/OutboxPublisher.java
backend/payment-processor/src/main/java/com/highpay/processor/application/usecase/ProcessPaymentCreatedUseCase.java
backend/payment-processor/src/main/java/com/highpay/processor/infrastructure/persistence/ProcessedEventRepositoryAdapter.java
backend/payment-processor/src/main/java/com/highpay/processor/infrastructure/client/HttpProviderClient.java
backend/payment-processor/src/main/java/com/highpay/processor/infrastructure/client/HttpPaymentServiceClient.java
```

## 41. Prompt Sugerido Para Continuar Depois

Voce pode abrir outro prompt e mandar algo assim:

```text
Leia explicacao.md, readme.md e docs/architecture-flows.md. Quero continuar o projeto HighPay a partir do estado atual. Primeiro me diga o status do projeto, depois sugira o proximo passo mais valioso para vaga backend/fintech, mantendo arquitetura hexagonal, confiabilidade e testes.
```

Ou, se quiser evoluir diretamente:

```text
Leia explicacao.md e implemente o proximo passo: correlation id + logs estruturados nos tres servicos, com testes e documentacao.
```

## 42. Resumo Executivo

HighPay agora e um projeto bom para discutir backend senior/pleno forte porque mostra mais do que CRUD.

Ele mostra:

- modelagem de dominio;
- idempotencia;
- eventos;
- consistencia eventual;
- transactional outbox;
- consumer inbox;
- retry seguro;
- DLQ;
- observabilidade;
- separacao por servicos;
- ports/adapters;
- testes;
- validacao E2E.

A narrativa principal e:

```text
Eu desenhei um fluxo de pagamento resistente a retries e falhas parciais. A criacao e idempotente, o evento e salvo via Outbox para nao ser perdido, o processamento e assincrono com RabbitMQ, o consumidor usa Inbox para deduplicar, a decisao do provider e persistida para retries seguros, e o payment-service mantem as regras de transicao do agregado Payment.
```

Essa e a historia que voce deve conseguir explicar com calma numa entrevista.

## 43. Observacao Sobre O Commit

Depois da criacao deste arquivo, o objetivo foi fazer um commit com o estado atual do projeto, incluindo:

- implementacao do `payment-service` evoluido;
- novo `payment-processor`;
- novo `provider-simulator`;
- documentacao em `readme.md`;
- documentacao tecnica em `docs/architecture-flows.md`;
- este arquivo `explicacao.md` como memoria completa para continuidade.

## 44. Atualizacao Posterior: Correlation ID E Logs Estruturados

Depois deste arquivo inicial, foi implementado o proximo passo de observabilidade:

- filtro HTTP nos tres servicos para aceitar ou gerar `X-Correlation-Id`;
- armazenamento do valor no MDC como `correlationId`;
- retorno do `X-Correlation-Id` no response HTTP;
- logs em formato `key=value` com `timestamp`, `level`, `service`, `correlationId`, `thread`, `logger` e `message`;
- inclusao de `correlationId` no payload `PaymentCreated`;
- publicacao do `X-Correlation-Id` como header da mensagem RabbitMQ;
- leitura do header no `payment-processor` ao consumir a mensagem;
- propagacao do header nas chamadas HTTP do processor para o `payment-service` e o `provider-simulator`;
- testes de filtro, listener e publisher atualizados;
- documentacao atualizada em `readme.md` e `docs/architecture-flows.md`.

Validacao feita:

```text
provider-simulator: 5 testes verdes
payment-service: 38 testes verdes
payment-processor: 23 testes verdes
```

Para rodar as suites completas foi necessario subir a infra local com:

```powershell
docker compose up -d
```
