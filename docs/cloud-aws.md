# HighPay - Cloud AWS

Este documento registra a experiencia de cloud adicionada ao projeto.

## O Que Existe

Foi criada uma referencia Terraform em:

```text
infra/aws/terraform
```

Ela modela:

- VPC;
- subnets publicas;
- Internet Gateway;
- route table publica;
- repositorios ECR para as imagens;
- cluster EKS de referencia.

## Objetivo

O objetivo nao e substituir o ambiente local. O objetivo e mostrar como o projeto sairia de Docker Compose/Kubernetes local para uma plataforma cloud.

Fluxo esperado em um ambiente real:

```text
GitHub Actions
  -> testes
  -> build Docker
  -> push ECR
  -> kubectl apply/Helm no EKS
```

## Comandos

Inicializar:

```powershell
cd infra/aws/terraform
terraform init
```

Planejar:

```powershell
terraform plan
```

Aplicar:

```powershell
terraform apply
```

## Observacao De Custo

EKS e recursos AWS geram custo real.

Use este Terraform como referencia tecnica. Antes de aplicar em uma conta real, revise custo, IAM, rede privada, NAT Gateway, RDS, Amazon MQ/MSK, secrets e politicas de seguranca.

## Evolucao Natural

Para producao, a arquitetura deveria evoluir para:

- RDS PostgreSQL em subnets privadas;
- Amazon MQ RabbitMQ ou MSK/Kafka;
- AWS Secrets Manager;
- AWS Load Balancer Controller;
- CloudWatch logs;
- OpenTelemetry Collector;
- ECR com lifecycle policy;
- Helm ou Argo CD para GitOps.

## Como Explicar Na Entrevista

Resumo:

```text
Eu deixei o projeto preparado para cloud com uma referencia Terraform para AWS/EKS/ECR. O deploy local roda em Docker Compose e Kubernetes; a evolucao natural e publicar imagens no ECR e aplicar os manifests no EKS por CI/CD.
```
