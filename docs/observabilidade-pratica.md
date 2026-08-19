# HighPay - Observabilidade Na Pratica

Este runbook mostra como observar o HighPay em execucao, tanto em Docker Compose quanto em Kubernetes.

## Objetivo

Observabilidade aqui nao e apenas "ter Grafana".

O objetivo e conseguir responder perguntas operacionais:

- os servicos estao vivos?
- o payment-service esta criando pagamentos?
- o Outbox esta publicando eventos?
- o processor esta consumindo mensagens?
- existem falhas de processamento?
- existe mensagem presa na DLQ?
- qual correlation id conecta logs dos tres servicos?

## Health Checks

Docker Compose:

```text
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
http://localhost:8083/actuator/health
```

Kubernetes:

```powershell
kubectl get pods -n highpay
kubectl describe pod -n highpay -l app=payment-service
kubectl logs -n highpay deployment/payment-service --tail=100
```

## Prometheus

Docker Compose:

```text
http://localhost:9090
```

Kubernetes:

```powershell
kubectl port-forward -n highpay service/prometheus 9090:9090
```

Depois acesse:

```text
http://localhost:9090
```

Queries uteis:

```promql
up{job=~"payment-service|payment-processor|provider-simulator"}
```

Mostra se Prometheus consegue coletar metricas dos servicos.

```promql
sum(increase(highpay_payment_created_total[5m]))
```

Mostra pagamentos criados nos ultimos 5 minutos.

```promql
sum(increase(highpay_payment_approved_total[5m]))
```

Mostra pagamentos aprovados nos ultimos 5 minutos.

```promql
sum(increase(highpay_processor_payment_created_event_consumed_total[5m]))
```

Mostra eventos `PaymentCreated` consumidos pelo processor.

```promql
sum(increase(highpay_processor_processing_failed_total[5m]))
```

Mostra falhas de processamento.

```promql
sum(highpay_outbox_event_pending)
```

Mostra eventos ainda pendentes no Outbox.

## Grafana

Docker Compose:

```text
http://localhost:3000
```

Kubernetes:

```powershell
kubectl port-forward -n highpay service/grafana 3000:3000
```

Acesse:

```text
http://localhost:3000
```

Credenciais no ambiente local:

```text
usuario: admin
senha: valor configurado em GRAFANA_ADMIN_PASSWORD ou no Secret Kubernetes
```

Dashboard:

```text
HighPay Overview
```

Ele mostra:

- pagamentos criados;
- pagamentos aprovados;
- falhas do processor;
- disponibilidade dos servicos.

## Logs E Correlation ID

Todos os servicos propagam:

```text
X-Correlation-Id
```

Exemplo de chamada:

```powershell
$correlationId = "debug-" + [guid]::NewGuid().ToString()

$body = @{
  merchantId = "merchant-observability"
  amount = 120.00
  currency = "BRL"
  paymentMethod = "PIX"
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri http://localhost:8081/api/v1/payments `
  -Method Post `
  -Headers @{
    "Idempotency-Key" = [guid]::NewGuid().ToString()
    "X-Correlation-Id" = $correlationId
  } `
  -ContentType "application/json" `
  -Body $body
```

Docker Compose:

```powershell
docker compose logs payment-service | Select-String $correlationId
docker compose logs payment-processor | Select-String $correlationId
docker compose logs provider-simulator | Select-String $correlationId
```

Kubernetes:

```powershell
kubectl logs -n highpay deployment/payment-service | Select-String $correlationId
kubectl logs -n highpay deployment/payment-processor | Select-String $correlationId
kubectl logs -n highpay deployment/provider-simulator | Select-String $correlationId
```

O mesmo correlation id deve aparecer no fluxo:

```text
payment-service recebe request
payment-service grava PaymentCreated com correlationId
payment-service publica RabbitMQ com header X-Correlation-Id
payment-processor consome evento
payment-processor chama provider-simulator
payment-processor chama payment-service interno
```

## RabbitMQ

Docker Compose:

```text
http://localhost:15672
```

Kubernetes:

```powershell
kubectl port-forward -n highpay service/rabbitmq 15672:15672
```

Filas importantes:

```text
highpay.payment-created.queue
highpay.payment-created.dlq
```

Comando em Kubernetes:

```powershell
kubectl exec -n highpay deployment/rabbitmq -- rabbitmqctl list_queues name messages_ready messages_unacknowledged
```

O estado esperado no caso feliz:

```text
highpay.payment-created.queue 0 0
highpay.payment-created.dlq   0 0
```

Se a DLQ tiver mensagens, significa que o retry do consumer esgotou e a mensagem precisa ser investigada.

## Banco De Dados

Docker Compose:

```powershell
docker compose exec postgres psql -U highpay -d highpay
```

Kubernetes:

```powershell
kubectl exec -it -n highpay deployment/postgres -- psql -U highpay -d highpay
```

Queries uteis:

```sql
SELECT status, count(*) FROM payments GROUP BY status;
SELECT status, count(*) FROM outbox_events GROUP BY status;
SELECT status, count(*) FROM processed_events GROUP BY status;
```

Leitura operacional:

- muitos `payments` em `CREATED`: evento pode nao estar sendo publicado;
- muitos `outbox_events` em `PENDING`: publisher ou RabbitMQ pode estar com problema;
- muitos `processed_events` em `FAILED`: processor, provider ou payment-service interno pode estar falhando;
- muitos pagamentos em `PROCESSING`: processor pode ter parado depois de marcar processamento.

## Alertas

Alertas configurados:

```text
HighPayServiceDown
HighPayPaymentProcessingFailures
HighPayOutboxPublishFailures
```

Eles ficam em:

```text
observability/prometheus/rules/highpay-alerts.yml
k8s/base/prometheus.yaml
```

Validacao no Prometheus:

```text
http://localhost:9090/alerts
```

## Cenarios Praticos Para Demonstrar

### 1. Servico Fora Do Ar

Kubernetes:

```powershell
kubectl scale deployment/provider-simulator -n highpay --replicas=0
```

Observe:

- `kubectl get pods -n highpay`;
- Prometheus query `up`;
- alerta `HighPayServiceDown`;
- falhas do processor se novos pagamentos forem criados.

Voltar:

```powershell
kubectl scale deployment/provider-simulator -n highpay --replicas=1
```

### 2. Falha De Processamento

Altere temporariamente o provider para responder erro usando configuracao ou simule indisponibilidade escalando para zero.

Observe:

- `highpay_processor_processing_failed_total`;
- `processed_events` com status `FAILED`;
- mensagens indo para DLQ se a falha persistir.

### 3. Backlog De Outbox

Pare o RabbitMQ temporariamente:

```powershell
kubectl scale deployment/rabbitmq -n highpay --replicas=0
```

Crie pagamentos.

Observe:

- `outbox_events` ficando `PENDING` ou `FAILED`;
- metrica de falha de publish;
- alerta de falha de Outbox.

Voltar:

```powershell
kubectl scale deployment/rabbitmq -n highpay --replicas=1
```

## Como Explicar Na Entrevista

Resumo:

```text
Eu implementei observabilidade pratica com health checks, metricas de negocio, metricas de processamento, Prometheus, Grafana, alertas e correlation id propagado entre HTTP, RabbitMQ e chamadas internas. Com isso consigo investigar se a falha esta na API, no Outbox, no RabbitMQ, no processor, no provider ou na atualizacao interna do pagamento.
```

O ponto mais importante:

```text
Observabilidade nao ficou limitada a infraestrutura. O projeto mede eventos de negocio e estados do fluxo de pagamento, como pagamentos criados, aprovados, falhas de processamento, eventos pendentes no Outbox e mensagens na DLQ.
```
