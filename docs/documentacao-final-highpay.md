# HighPay - Documentacao Final do Projeto

Este documento explica o objetivo do HighPay, o que foi construido, quais decisoes tecnicas foram tomadas e por que elas fazem sentido em um sistema de pagamentos.

Observacao: o link externo do objetivo inicial nao ficou acessivel neste ambiente. A secao de objetivo abaixo foi escrita com base no estado real do repositorio, nas documentacoes existentes e no rumo que o projeto tomou. Se o texto original for colado depois, a introducao pode ser ajustada sem mudar a documentacao tecnica.

## 1. Objetivo Inicial

O HighPay nasceu como um projeto de preparacao para uma vaga backend mais forte, com foco em arquitetura, confiabilidade e sistemas financeiros.

A intencao nao era criar apenas um CRUD de pagamentos. O objetivo era construir uma simulacao realista de processamento de pagamentos PIX, mostrando preocupacoes que aparecem em ambientes de producao:

- evitar pagamento duplicado quando o cliente repete uma requisicao;
- nao perder eventos quando a aplicacao cai entre banco e mensageria;
- processar pagamentos de forma assincrona;
- lidar com retries, falhas parciais e mensagens duplicadas;
- simular integracao com um provider externo;
- expor metricas, health checks, logs correlacionados e dashboards;
- organizar o codigo em camadas claras, com regras de negocio separadas de infraestrutura.

Em uma frase, o projeto demonstra como desenhar um fluxo de pagamento resiliente a retries e falhas parciais usando Java, Spring Boot, PostgreSQL, RabbitMQ, Docker e observabilidade.

## 2. Problema Que O Projeto Simula

Em pagamentos, falhas parciais sao normais.

Um cliente pode enviar um pagamento, receber timeout e tentar novamente. O backend pode salvar o pagamento no banco e cair antes de publicar o evento. O RabbitMQ pode entregar a mesma mensagem mais de uma vez. O provider externo pode aprovar um pagamento, mas o servico interno pode falhar ao salvar esse resultado.

O HighPay foi desenhado para tratar esses cenarios sem depender do "caso feliz".

## 3. Resultado Final

O projeto ficou dividido em tres servicos principais:

- `payment-service`: API principal de pagamentos.
- `payment-processor`: worker que consome eventos e processa pagamentos.
- `provider-simulator`: simulador de provider/adquirente externo.

Tambem foi adicionado um frontend React em `frontend`, usado como console operacional para criar, listar e acompanhar pagamentos.

A infraestrutura local inclui:

- PostgreSQL;
- RabbitMQ com fila principal e DLQ;
- Nginx como gateway/load balancer;
- Prometheus;
- Grafana;
- Docker Compose local;
- overlay de producao com opcoes de seguranca e mTLS;
- scripts de E2E, carga e geracao de certificados locais.
- pipeline CI/CD com GitHub Actions;
- referencia cloud AWS/EKS/ECR com Terraform.

## 4. Arquitetura Geral

Fluxo principal:

```text
Cliente
  -> payment-service
      -> PostgreSQL payments
      -> PostgreSQL outbox_events
      -> RabbitMQ
          -> payment-processor
              -> provider-simulator
              -> payment-service endpoints internos
                  -> PostgreSQL payments atualizado
```

O `payment-service` e o dono do agregado `Payment`. O `payment-processor` nao altera o banco dele diretamente. Quando precisa mudar status, ele chama endpoints internos do proprio `payment-service`.

Essa decisao preserva as regras de negocio dentro do servico que realmente possui o pagamento.

## 5. Decisoes Arquiteturais

### 5.1 Ports & Adapters

O projeto usa uma arquitetura inspirada em Ports & Adapters.

Separacao principal:

```text
domain
  regras de negocio

application
  casos de uso e portas

interfaces
  controllers, requests e responses

infrastructure
  JPA, RabbitMQ, HTTP clients, observabilidade e detalhes externos
```

Motivo da decisao:

O use case nao deve depender diretamente de JPA, RabbitMQ ou Micrometer. Ele depende de contratos da aplicacao. Isso deixa a regra de negocio mais facil de testar, explicar e evoluir.

Exemplo:

```text
CreatePaymentUseCase -> PaymentRepository
PaymentRepositoryAdapter -> JpaPaymentRepository -> PostgreSQL
```

A porta e o contrato. O adapter e a implementacao tecnica desse contrato.

### 5.2 Payment-Service Como Dono Do Pagamento

O `payment-service` concentra as regras de transicao de status:

```text
CREATED -> PROCESSING -> APPROVED
CREATED -> PROCESSING -> REJECTED
CREATED -> PROCESSING -> FAILED
```

O processor nao faz `UPDATE payments` direto no banco.

Motivo da decisao:

Se outro servico alterasse a tabela diretamente, ele poderia violar regras do dominio ou ficar acoplado ao schema interno. Chamando endpoints internos, a alteracao passa pelos use cases e pelo dominio `Payment`.

### 5.3 Idempotencia HTTP

A criacao de pagamento exige `Idempotency-Key`.

Se o cliente repetir a mesma requisicao com a mesma chave, o sistema retorna o mesmo pagamento em vez de criar outro.

Motivo da decisao:

Em pagamentos, retries sao comuns. Sem idempotencia, um timeout poderia virar duas cobrancas.

O projeto tambem salva um fingerprint da request original. Se a mesma chave for reutilizada com payload diferente, o sistema responde conflito.

```text
mesma chave + mesmo payload      -> retorna pagamento existente
mesma chave + payload diferente  -> 409 Conflict
```

### 5.4 Transactional Outbox

Ao criar um pagamento, o `payment-service` salva na mesma transacao:

- o registro em `payments`;
- o evento `PaymentCreated` em `outbox_events`.

Depois, um publisher agendado publica eventos pendentes no RabbitMQ.

Motivo da decisao:

Banco e RabbitMQ nao compartilham uma transacao atomica. Sem Outbox, a aplicacao poderia salvar o pagamento e cair antes de publicar o evento. Com Outbox, a intencao de publicar fica persistida.

### 5.5 RabbitMQ Para Processamento Assincrono

O pagamento nao e processado completamente dentro da request HTTP de criacao. A API cria o pagamento e publica um evento. O processor consome esse evento depois.

Motivo da decisao:

Chamadas ao provider externo podem ser lentas ou instaveis. Tirar isso da request publica melhora isolamento, permite retry e deixa o fluxo mais parecido com sistemas reais de pagamento.

### 5.6 Inbox No Consumer

O `payment-processor` usa a tabela `processed_events`.

Ela controla:

```text
PROCESSING
PROCESSED
FAILED
```

Motivo da decisao:

RabbitMQ entrega mensagens no modelo "at least once". Isso significa que a mesma mensagem pode chegar mais de uma vez. O Inbox evita que o processor chame o provider duplicadamente para o mesmo evento.

### 5.7 Decisao Do Provider Persistida

Depois que o provider responde, o processor salva no Inbox:

- `provider_status`;
- `provider_transaction_id`.

Motivo da decisao:

Se o provider aprovar e o `payment-service` falhar na hora de salvar `/approve`, o retry futuro nao deve chamar o provider novamente. Ele deve reaplicar a decisao ja salva.

Essa foi uma das decisoes mais importantes do projeto.

```text
provider decidiu SUCCESS
  -> salva decisao no Inbox
  -> tenta aplicar APPROVED no payment-service
  -> se falhar, retry reaplica APPROVED sem chamar provider novamente
```

### 5.8 Transicoes Internas Idempotentes

As rotas internas aceitam repeticoes seguras.

Exemplo:

```text
processor chama /approve
payment-service salva APPROVED
processor cai antes de marcar Inbox como PROCESSED
RabbitMQ entrega de novo
processor chama /approve novamente
```

A segunda chamada deve funcionar se for a mesma decisao do provider.

Motivo da decisao:

Timeouts podem acontecer depois do servidor ja ter gravado no banco. Para retry ser seguro, o estado final precisa aceitar reaplicacao da mesma decisao.

### 5.9 Retry E DLQ

O consumer RabbitMQ tem retry configurado. Se a falha persistir, a mensagem vai para a DLQ.

Motivo da decisao:

Sem retry, falhas temporarias iriam para erro cedo demais. Sem DLQ, mensagens ruins poderiam ficar em loop infinito. Com retry e DLQ, o sistema tenta recuperar falhas temporarias e isola falhas persistentes para analise.

### 5.10 JSON Estruturado

O projeto usa Jackson para serializar e desserializar eventos e requests internas.

Motivo da decisao:

Montar JSON por string ou ler JSON com regex e fragil. Campos podem vir em outra ordem, com espacos, escapes ou campos extras. Usar `ObjectMapper` deixa o contrato mais robusto.

### 5.11 Observabilidade

Foram adicionados:

- Actuator health checks;
- metricas Micrometer;
- Prometheus;
- Grafana;
- dashboard de visao geral;
- alertas Prometheus;
- logs estruturados em `key=value`;
- propagacao de `X-Correlation-Id` entre HTTP, RabbitMQ e chamadas internas.

Motivo da decisao:

Em sistema distribuido, nao basta o fluxo funcionar. E preciso saber onde ele falhou, quantas mensagens estao pendentes, quantos pagamentos foram aprovados/rejeitados, se ha DLQ e qual request gerou quais logs.

### 5.12 Seguranca Interna

Os endpoints internos exigem `X-Internal-Service-Token`.

Tambem existe suporte de ambiente para mTLS via compose de producao e scripts de certificado local.

Motivo da decisao:

Rotas internas como `/approve`, `/reject` e `/fail` nao devem ficar abertas como API publica. Mesmo em projeto local, separar API publica de comunicacao interna mostra maturidade arquitetural.

### 5.13 Timeouts E Circuit Breaker

Os clients HTTP do processor possuem timeout configuravel. Tambem foi implementado circuit breaker simples para chamadas ao provider e ao payment-service.

Motivo da decisao:

Um worker nao pode ficar preso indefinidamente esperando uma dependencia. Timeout e circuit breaker reduzem bloqueio de threads e deixam retry/DLQ agir.

### 5.14 Retencao Operacional

Foram criados jobs de retencao para Outbox e Inbox.

Motivo da decisao:

Tabelas operacionais crescem com o tempo. Mesmo em projeto de estudo, prever retencao mostra preocupacao com operacao continua.

## 6. Fluxo Principal De Pagamento

1. Cliente chama `POST /api/v1/payments` com `Idempotency-Key`.
2. `payment-service` valida request e fingerprint.
3. Se for novo, cria `Payment` com status `CREATED`.
4. Na mesma transacao, grava `PaymentCreated` em `outbox_events`.
5. `OutboxPublisher` publica o evento no RabbitMQ.
6. `payment-processor` consome a mensagem.
7. Processor reserva o evento em `processed_events`.
8. Processor marca pagamento como `PROCESSING`.
9. Processor chama `provider-simulator`.
10. Processor salva decisao do provider no Inbox.
11. Processor chama `/approve`, `/reject` ou `/fail` no `payment-service`.
12. `payment-service` aplica a transicao de dominio.
13. Processor marca evento como `PROCESSED`.

Resultado esperado no caso feliz:

```text
CREATED -> PROCESSING -> APPROVED
```

## 7. Falhas Que O Projeto Cobre

### Cliente Repete POST

Protecao: `Idempotency-Key` e fingerprint.

### Payment Criado, Aplicacao Cai Antes Do RabbitMQ

Protecao: Transactional Outbox.

### RabbitMQ Entrega Mensagem Duplicada

Protecao: Inbox com `processed_events`.

### Provider Decide, Mas Payment-Service Falha

Protecao: decisao do provider persistida no Inbox e reaplicada no retry.

### Timeout Em Chamada Interna

Protecao: timeouts, retry, Inbox e DLQ.

### Mensagem Ruim Ou Falha Persistente

Protecao: retry controlado e DLQ.

### Duplicidade Depois De Estado Final

Protecao: transicoes internas idempotentes, aceitando a mesma decisao e rejeitando decisao contraditoria.

## 8. Infraestrutura Docker

O `compose.yaml` sobe a stack local completa.

Principais portas:

```text
payment-service:     http://localhost:8081
payment-processor:   http://localhost:8082
provider-simulator:  http://localhost:8083
RabbitMQ UI:         http://localhost:15672
Prometheus:          http://localhost:9090
Grafana:             http://localhost:3000
```

O `compose.prod.yaml` adiciona preocupacoes de ambiente mais proximo de producao, como gateway e configuracoes de seguranca.

O arquivo `.env` e local e nao deve ser commitado. O repositorio mantem `env.example` como referencia segura.

## 9. Como Rodar Localmente

Crie o `.env`:

```powershell
Copy-Item env.example .env
```

Suba tudo:

```powershell
docker compose up -d --build
```

Rode o E2E:

```powershell
.\scripts\e2e-local.ps1
```

Verifique os health checks:

```text
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
http://localhost:8083/actuator/health
```

## 10. Como Testar

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

Validacao ja realizada no projeto:

```text
payment-service: 49 testes passando
payment-processor: 29 testes passando
provider-simulator: 5 testes passando
E2E local: pagamento finalizado como APPROVED
```

## 11. Como Explicar Em Entrevista

Resumo curto:

```text
Eu construi uma plataforma ficticia de pagamento PIX com criacao idempotente, persistencia em PostgreSQL, publicacao confiavel de eventos com Transactional Outbox, processamento assincrono com RabbitMQ, deduplicacao do consumidor com Inbox, retry/DLQ, provider externo simulado, observabilidade e separacao em Ports & Adapters.
```

Pergunta: por que idempotencia?

```text
Porque em pagamentos o cliente pode repetir uma request por timeout. Com Idempotency-Key e fingerprint, a mesma tentativa retorna o mesmo pagamento e payload diferente com a mesma chave vira conflito.
```

Pergunta: por que Outbox?

```text
Porque salvar no banco e publicar no RabbitMQ nao e uma unica transacao atomica. O Outbox salva a intencao de publicar junto com o pagamento, evitando perda de evento.
```

Pergunta: por que Inbox?

```text
Porque RabbitMQ pode entregar a mesma mensagem mais de uma vez. O Inbox controla eventId, status e decisao do provider para evitar processamento duplicado e permitir retry seguro.
```

Pergunta: por que salvar a decisao do provider?

```text
Porque depois que o provider externo aprovou ou rejeitou, o retry nao deve chama-lo de novo. Ele deve reaplicar internamente a decisao ja salva.
```

Pergunta: por que o processor nao atualiza o banco direto?

```text
Porque o payment-service e dono do agregado Payment. As regras de transicao ficam no dominio dele; o processor chama endpoints internos para preservar essas regras.
```

## 12. Estado Do Git

O historico local foi reescrito para ficar limpo, com um commit raiz representando o baseline do projeto.

O `.env` foi removido do versionamento e permanece ignorado. O arquivo correto para exemplo de configuracao e `env.example`.

Se for necessario atualizar o repositorio remoto depois da reescrita de historico, sera preciso fazer push forcado com cuidado:

```powershell
git push --force-with-lease origin main
```

Esse comando ainda deve ser executado apenas quando houver certeza de que o remoto pode ser sobrescrito.

## 13. Proximos Passos Opcionais

O projeto ja esta em um bom ponto para apresentacao tecnica. Melhorias possiveis:

- adicionar pipeline CI rodando testes dos tres servicos;
- criar contract tests entre `payment-service` e `payment-processor`;
- adicionar tracing distribuido com OpenTelemetry;
- fortalecer gestao de secrets para ambiente real;
- criar runbook operacional para DLQ, outbox failed e incidentes;
- executar teste de carga mais longo com multiplas instancias.

## 14. Conclusao

O HighPay ficou estruturado como um projeto de backend voltado a confiabilidade.

Ele demonstra dominio de fundamentos importantes para sistemas financeiros: idempotencia, consistencia eventual, mensageria, Outbox, Inbox, retry seguro, DLQ, observabilidade, seguranca interna e separacao de responsabilidades.

Mais importante que a quantidade de codigo, o projeto agora tem uma narrativa tecnica coerente: cada decisao existe para reduzir risco real de duplicidade, perda de evento, falha parcial ou dificuldade operacional.
