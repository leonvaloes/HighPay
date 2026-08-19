# HighPay - Full Stack, CI/CD E Seguranca

Este documento resume os itens adicionados para cobrir os requisitos restantes da vaga.

## React

Frontend:

```text
frontend
```

Funcionalidades:

- tela operacional de pagamentos;
- criacao de pagamento com `Idempotency-Key`;
- listagem de pagamentos;
- consulta de detalhe;
- polling de status assincrono;
- campo para JWT;
- link para Swagger;
- indicador de health da API.

Rodar:

```powershell
cd frontend
npm install
npm run dev
```

## OpenAPI/Swagger

O `payment-service` usa `springdoc-openapi`.

Endpoints:

```text
http://localhost:8081/swagger-ui.html
http://localhost:8081/v3/api-docs
```

## JWT Na API Publica

Foi adicionado filtro JWT opcional para rotas:

```text
/api/v1/**
```

Configuracao:

```properties
highpay.public-auth.enabled=false
highpay.public-auth.jwt-secret=
highpay.public-auth.issuer=highpay-local
```

Em local, fica desligado por padrao para nao quebrar o E2E. Para ligar:

```powershell
$env:HIGHPAY_PUBLIC_AUTH_ENABLED="true"
$env:HIGHPAY_PUBLIC_AUTH_JWT_SECRET="uma-chave-com-pelo-menos-32-caracteres"
```

Gerar token:

```powershell
.\scripts\generate-jwt.ps1 -Secret "uma-chave-com-pelo-menos-32-caracteres"
```

Usar:

```http
Authorization: Bearer <token>
```

## CI/CD

Pipeline:

```text
.github/workflows/ci.yml
```

Jobs:

- testes Maven dos tres servicos;
- lint/build do frontend React;
- build Docker das quatro imagens;
- renderizacao dos manifests Kubernetes.

## Como Explicar Na Entrevista

Resumo:

```text
Eu completei o projeto como uma stack full stack: backend Java/Spring Boot, frontend React, API documentada com OpenAPI/Swagger, JWT opcional na API publica, CI/CD com GitHub Actions, Docker, Kubernetes e referencia cloud AWS.
```
