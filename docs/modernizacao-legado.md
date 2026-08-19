# HighPay - Modernizacao De Legado

Este documento cria a narrativa de modernizacao pedida pela vaga: sair de um fluxo legado sincronico e acoplado para uma arquitetura moderna com microsservicos, eventos, observabilidade e deploy em containers/Kubernetes.

## Cenario Legado Simulado

O sistema legado de pagamentos tinha estas caracteristicas:

- uma aplicacao monolitica recebia o pagamento;
- a request HTTP chamava o provider externo diretamente;
- o mesmo fluxo gravava banco, chamava provider e atualizava status;
- nao havia `Idempotency-Key`;
- nao havia Outbox;
- nao havia DLQ;
- logs nao tinham correlation id;
- falhas parciais eram dificeis de rastrear;
- escalar processamento exigia escalar a aplicacao inteira.

Fluxo legado:

```text
Cliente
  -> monolito
      -> banco
      -> provider externo
      -> banco
  -> resposta HTTP final
```

## Problemas Do Legado

### Duplicidade

Se o cliente recebia timeout e repetia a request, o sistema podia criar duas tentativas de pagamento.

### Falha Parcial

Se o banco gravava o pagamento, mas a chamada ao provider falhava ou a aplicacao caia no meio, ficava dificil saber se era seguro tentar de novo.

### Baixa Observabilidade

Sem correlation id e metricas de negocio, investigar um pagamento exigia procurar logs soltos e consultar banco manualmente.

### Escalabilidade Limitada

O processamento dependia da request publica. Se o provider ficasse lento, threads HTTP ficavam presas.

## Estrategia De Modernizacao

A modernizacao foi feita por etapas, reduzindo risco:

1. Criar API REST versionada para pagamentos.
2. Adicionar idempotencia HTTP com fingerprint.
3. Separar regras de negocio em dominio e use cases.
4. Adicionar Outbox para registrar eventos na mesma transacao do pagamento.
5. Publicar eventos no RabbitMQ.
6. Criar `payment-processor` como worker assincrono.
7. Criar Inbox para deduplicar consumo.
8. Persistir decisao do provider para retry seguro.
9. Adicionar retry, DLQ e reprocessamento manual.
10. Adicionar observabilidade com metricas, logs e correlation id.
11. Empacotar com Docker.
12. Preparar Kubernetes para operacao com replicas, probes e HPA.

## Arquitetura Moderna

```text
Cliente/Frontend React
  -> payment-service
      -> PostgreSQL payments
      -> PostgreSQL outbox_events
      -> RabbitMQ
          -> payment-processor
              -> provider-simulator
              -> payment-service endpoints internos
```

## Decisoes Importantes

### Strangler Fig

Em um ambiente real, a migracao poderia usar o padrao Strangler Fig:

- manter o legado atendendo fluxos antigos;
- criar uma API nova para novos pagamentos;
- rotear gradualmente trafego para o HighPay;
- migrar capacidades por dominio, nao por tela.

### Eventos Antes De Integracao Direta

O fluxo moderno usa evento `PaymentCreated` em vez de processar tudo na request.

Motivo:

- reduzir acoplamento;
- permitir retry;
- suportar escala horizontal;
- isolar lentidao do provider.

### Banco Como Fonte Do Estado De Negocio

O `payment-service` continua dono do estado de pagamento. O processor nao escreve direto em `payments`.

Motivo:

- preservar invariantes do dominio;
- evitar duplicacao de regra;
- reduzir acoplamento entre servicos.

## Como Explicar Na Entrevista

Resumo:

```text
Eu modelei uma modernizacao incremental: parti de um fluxo legado sincronico e acoplado e evolui para microsservicos orientados a eventos. A nova arquitetura adiciona idempotencia, Outbox, Inbox, RabbitMQ, DLQ, observabilidade, Docker, Kubernetes e frontend React para operacao.
```

Ponto forte:

```text
A modernizacao nao foi apenas tecnica. Cada decisao ataca um risco real do dominio de pagamentos: duplicidade, perda de evento, retry inseguro, baixa rastreabilidade e dificuldade de escalar processamento.
```
