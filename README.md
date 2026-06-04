# package-service

Microsserviço de **gestão de pacotes**. É a porta de entrada do sistema: cria pacotes,
controla o **ciclo de vida** (máquina de estados) e dispara a roteirização pedindo ao
`logistics-service` (de forma assíncrona) o cálculo da rota.

Comunica-se com o `logistics-service` exclusivamente por mensagens SQS FIFO (nunca por
HTTP direto).

- Porta local: **http://localhost:8081** (via `docker compose`, container expõe 8080)
- Banco: MongoDB (`package_db`) — precisa ser **replica set** (transações do outbox/inbox)
- Mensageria: SQS FIFO (MiniStack no ambiente local)
- Não usa Redis.

---

## Arquitetura (hexagonal, 4 módulos Gradle)

`domain` → `application` (use cases) → `infrastructure` → `app`. As dependências apontam
para dentro; o `domain` é Java puro + Jakarta Validation (sem Spring).

- **domain**: `Package`, `PackageStatus`, `RouteInfo`, eventos, exceções e _ports_. A
  **máquina de estados** vive em `Package`/`PackageStatus`.
- **application**: casos de uso (criar, consultar, listar, atualizar status, mudar destino,
  processar rota calculada/falha).
- **infrastructure**: adaptadores Mongo, SQS, outbox/inbox, `UseCaseConfig`, `MongoConfig`.
- **app**: classe principal, `PackageRestAdapter`, `GlobalExceptionHandler` (RFC-7807).

---

## Conceitos de domínio

| Conceito | Descrição |
|----------|-----------|
| **Package** | Pacote: `senderCep`, `recipientCep`, `weight`, `status`, `description`, `routeInfo`, timestamps. |
| **PackageStatus** | Estado do pacote dentro de uma **máquina de estados** estrita. |
| **RouteInfo** | Resumo da rota recebido do logistics: lista de hubs, distância e entrega estimada. |

---

## Regras de negócio

1. **Criação:** exige `senderCep`/`recipientCep` (8 dígitos), `weight > 0` e `description`
   opcional (≤ 500 caracteres). O pacote nasce no status **`CREATED`** e dispara o cálculo
   de rota (evento `package.created`).
2. **Máquina de estados** (transições permitidas):

   | De | Para |
   |----|------|
   | `CREATED` | `ROUTE_PENDING`, `ROUTE_CALCULATED`, `FAILED` |
   | `ROUTE_PENDING` | `ROUTE_PENDING`, `ROUTE_CALCULATED`, `FAILED` |
   | `ROUTE_CALCULATED` | `ROUTE_PENDING`, `IN_TRANSIT`, `FAILED` |
   | `IN_TRANSIT` | `DELIVERED`, `FAILED` |
   | `DELIVERED` | — (terminal) |
   | `FAILED` | — (terminal) |

   Qualquer transição fora da tabela é rejeitada (`InvalidPackageStateException`).
3. **Mudança de destino** só é permitida **antes do transporte** (`CREATED`, `ROUTE_PENDING`
   ou `ROUTE_CALCULATED`). Ela reabre a roteirização: o status volta para **`ROUTE_PENDING`**
   e um evento `package.destination.changed` é emitido. Depois de `IN_TRANSIT`/`DELIVERED`/
   `FAILED`, a mudança é rejeitada.
4. **Rota calculada:** ao receber `route.calculated`/`route.recalculated`, o pacote recebe
   o `routeInfo` e vai para **`ROUTE_CALCULATED`**.
5. **Falha de rota:** ao receber `route.failed`, o pacote vai para **`FAILED`** (falha
   tratada, sem ficar "preso").
6. **Idempotência (inbox):** eventos consumidos são registrados por `eventId`; reentregas
   são ignoradas.
7. **Publicação confiável (outbox):** eventos de saída (`package.created`,
   `package.status.updated`, `package.destination.changed`) são gravados no **outbox** na
   mesma transação da escrita do pacote e publicados por um _relay_ agendado (com retry).

---

## Fluxos de negócio

### Fluxo 1 — Criação de pacote
`POST /api/v1/packages` → valida entrada → grava o pacote em `CREATED` **e** o evento
`package.created` no outbox (mesma transação) → `201 Created`. O _relay_ publica o evento;
o `logistics-service` calculará a rota de forma assíncrona.

### Fluxo 2 — Roteirização (assíncrona)
1. `logistics-service` calcula a rota e publica `route.calculated` em
   `logistics-events-queue.fifo`.
2. `PackageEventListenerAdapter` consome; verifica o **inbox** (idempotência).
3. `ProcessRouteCalculatedUseCase` carrega o pacote, anexa o `routeInfo` e o move para
   **`ROUTE_CALCULATED`**.
4. Consultar `GET /api/v1/packages/{id}` mostra o status atualizado e o `routeInfo`.

### Fluxo 3 — Falha de roteirização (assíncrona)
Se a rota não puder ser calculada (sem hub na cidade/estado, CEP inexistente, grafo sem
caminho), o logistics publica `route.failed`. `ProcessRouteFailedUseCase` move o pacote para
**`FAILED`** (idempotente; ignora se já terminal). Sem esse fluxo, o pacote ficaria preso em
`CREATED`/`ROUTE_PENDING` indefinidamente.

### Fluxo 4 — Atualização de status
`PATCH /api/v1/packages/{id}/status` → valida a transição na máquina de estados → atualiza →
emite `package.status.updated`. Transição inválida → `4xx`.

> **Atenção (formato do corpo):** a validação ocorre sobre o corpo, que exige **`packageId`
> e `newStatus`**. Envie o `packageId` igual ao do path (o id do path prevalece). Sem o
> `packageId` no corpo, retorna `400`.

### Fluxo 5 — Mudança de destino + recálculo
`PATCH /api/v1/packages/{id}/destination` → valida o estado (só antes do transporte) →
muda o CEP, volta para **`ROUTE_PENDING`** e emite `package.destination.changed` (com o
`senderCep`, para o logistics recalcular corretamente). O recálculo traz o pacote de volta a
`ROUTE_CALCULATED`.

### Fluxo 6 — Consulta e listagem
- `GET /api/v1/packages/{id}` — detalha um pacote (`404` se não existir).
- `GET /api/v1/packages` — lista todos; aceita filtro `?status=` (ex.: `?status=DELIVERED`).

---

## Mensageria

| Direção | Fila | Eventos |
|---------|------|---------|
| **Produz**  | `package-events-queue.fifo` | `package.created`, `package.status.updated`, `package.destination.changed` |
| **Consome** | `logistics-events-queue.fifo` | `route.calculated`, `route.recalculated`, `route.failed` |

FIFO: `MessageGroupId = packageId` (ordem por pacote), `MessageDeduplicationId = eventId`.
Garantias: **outbox** (publicação atômica + retry) e **inbox** (idempotência no consumo).

---

## Falhas tratadas

| Situação | Comportamento |
|----------|---------------|
| CEP inválido (≠ 8 dígitos), peso ≤ 0, campo obrigatório ausente, `description` > 500 | `400` (RFC-7807) |
| Transição de status inválida | `4xx` (`InvalidPackageStateException`) |
| Mudança de destino após o transporte | `4xx` |
| Pacote inexistente | `404` |
| Rota impossível (assíncrona) | Pacote vai para `FAILED` via `route.failed` |

---

## Endpoints

| Método | Caminho | Corpo | Sucesso |
|--------|---------|-------|---------|
| POST | `/api/v1/packages` | `CreatePackageCommand` | 201 |
| GET | `/api/v1/packages/{id}` | — | 200 / 404 |
| GET | `/api/v1/packages?status=` | — | 200 |
| PATCH | `/api/v1/packages/{id}/status` | `{packageId,newStatus}` | 200 / 4xx |
| PATCH | `/api/v1/packages/{id}/destination` | `{newCep}` | 200 / 4xx |

Actuator em `/management` (`health`, `info`, `metrics`, `loggers`).

---

## Como executar e testar

```bash
# stack completo (os dois serviços + Mongo + MiniStack), a partir da raiz do repositório:
docker compose up -d --build
```

Para que a roteirização funcione, o `logistics-service` precisa ter hubs/conexões — o
**Data Seeder** dele popula um grafo padrão automaticamente no perfil `local`.

Testes HTTP prontos em [`http/packages.http`](http/packages.http). Guia de validação manual
ponta a ponta em [`../VALIDATION.md`](../VALIDATION.md).

