# HighPay - Kubernetes

Este documento mostra como rodar o HighPay em Kubernetes local e quais conceitos da vaga ficam cobertos com essa camada.

## O Que Foi Adicionado

Os manifests ficam em:

```text
k8s/base
```

Recursos incluidos:

- `Namespace` dedicado: `highpay`;
- `Secret` com credenciais de desenvolvimento;
- `Deployment` e `Service` para `payment-service`;
- `Deployment` e `Service` para `payment-processor`;
- `Deployment` e `Service` para `provider-simulator`;
- `Deployment` e `Service` para o frontend React;
- `Deployment`, `Service` e `PersistentVolumeClaim` para PostgreSQL;
- `Deployment`, `Service` e `PersistentVolumeClaim` para RabbitMQ;
- Prometheus dentro do cluster;
- Grafana dentro do cluster;
- `HorizontalPodAutoscaler` para API e worker;
- `Ingress` para API, Prometheus e Grafana.

## Imagens Locais

Os manifests usam estas imagens:

```text
highpay/payment-service:local
highpay/payment-processor:local
highpay/provider-simulator:local
highpay/frontend:local
```

Build:

```powershell
docker build -t highpay/payment-service:local backend/payment-service
docker build -t highpay/payment-processor:local backend/payment-processor
docker build -t highpay/provider-simulator:local backend/provider-simulator
docker build -t highpay/frontend:local frontend
```

Se estiver usando `kind`, carregue as imagens no cluster:

```powershell
kind load docker-image highpay/payment-service:local
kind load docker-image highpay/payment-processor:local
kind load docker-image highpay/provider-simulator:local
kind load docker-image highpay/frontend:local
```

No Docker Desktop Kubernetes, normalmente as imagens locais ja ficam disponiveis para o cluster.

## Aplicar No Cluster

```powershell
kubectl apply -k k8s/base
```

Verificar recursos:

```powershell
kubectl get all -n highpay
kubectl get pvc -n highpay
kubectl get hpa -n highpay
kubectl get ingress -n highpay
```

Esperar rollout:

```powershell
kubectl rollout status deployment/postgres -n highpay
kubectl rollout status deployment/rabbitmq -n highpay
kubectl rollout status deployment/provider-simulator -n highpay
kubectl rollout status deployment/payment-service -n highpay
kubectl rollout status deployment/payment-processor -n highpay
kubectl rollout status deployment/prometheus -n highpay
kubectl rollout status deployment/grafana -n highpay
```

## Acessos Locais Com Port-Forward

API:

```powershell
kubectl port-forward -n highpay service/payment-service 8081:8081
```

Processor health:

```powershell
kubectl port-forward -n highpay service/payment-processor 8082:8082
```

Provider:

```powershell
kubectl port-forward -n highpay service/provider-simulator 8083:8083
```

RabbitMQ Management:

```powershell
kubectl port-forward -n highpay service/rabbitmq 15672:15672
```

Prometheus:

```powershell
kubectl port-forward -n highpay service/prometheus 9090:9090
```

Grafana:

```powershell
kubectl port-forward -n highpay service/grafana 3000:3000
```

## Teste Rapido Da API

Com o port-forward da API ativo:

```powershell
$body = @{
  merchantId = "merchant-k8s"
  amount = 100.00
  currency = "BRL"
  paymentMethod = "PIX"
} | ConvertTo-Json

$headers = @{
  "Idempotency-Key" = [guid]::NewGuid().ToString()
  "X-Correlation-Id" = "manual-k8s-test"
}

Invoke-RestMethod `
  -Uri http://localhost:8081/api/v1/payments `
  -Method Post `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body
```

## Probes

Os servicos Java usam:

```text
/actuator/health
```

Kubernetes usa esse endpoint em `readinessProbe` e `livenessProbe`.

Na pratica:

- readiness decide se o Pod pode receber trafego;
- liveness decide se o Pod precisa ser reiniciado;
- se PostgreSQL, RabbitMQ ou a aplicacao falham, o estado aparece no `kubectl describe pod`.

Comando:

```powershell
kubectl describe pod -n highpay -l app=payment-service
```

## HPA

Foram adicionados HPAs para:

```text
payment-service
payment-processor
```

Configuracao:

```text
minReplicas: 2
maxReplicas: 5
target CPU: 70%
```

Para o HPA funcionar de verdade, o cluster precisa do Metrics Server instalado.

Verificacao:

```powershell
kubectl top pods -n highpay
kubectl get hpa -n highpay
```

## Ingress

O ingress espera um controller Nginx e usa os hosts:

```text
highpay.local
grafana.highpay.local
prometheus.highpay.local
```

Em ambiente local, adicione no arquivo de hosts:

```text
127.0.0.1 highpay.local grafana.highpay.local prometheus.highpay.local
```

Se nao houver ingress controller instalado, use `kubectl port-forward`.

## Como Explicar Na Entrevista

Resumo:

```text
Eu empacotei a stack em Kubernetes com Deployments, Services, PVCs, Secrets, probes, HPA, Ingress e observabilidade no proprio cluster. Isso mostra o caminho de Docker Compose local para um ambiente orquestrado, com escalabilidade horizontal e operacao mais proxima de producao.
```

Pontos fortes:

- `payment-service` e `payment-processor` rodam com 2 replicas;
- Outbox usa lock no banco para reduzir publicacao duplicada com multiplas instancias;
- Inbox protege o processor contra mensagens duplicadas;
- readiness/liveness tornam falhas visiveis para o orquestrador;
- Prometheus e Grafana rodam dentro do cluster;
- HPA mostra preparacao para escalabilidade horizontal.

## Limpeza

```powershell
kubectl delete -k k8s/base
```
