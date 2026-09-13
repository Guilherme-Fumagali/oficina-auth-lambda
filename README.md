# oficina-auth-lambda

Function serverless de **autenticação por CPF** do Tech Challenge Fase 3 — PosTech FIAP, Arquitetura de Software.

Um dos quatro repositórios da entrega. Os outros: [`oficina-api`](../oficina-api) (aplicação), `oficina-infra-k8s` (rede, EKS, API Gateway) e `oficina-infra-db` (RDS).

## Propósito

Implementa o requisito de autenticação da fase: **validar o CPF do cliente, consultar sua existência e status na base de dados, e devolver um token JWT** válido para consumo das APIs protegidas.

A partir desta fase a aplicação principal deixou de emitir tokens — ela apenas valida. Um único emissor significa um lugar para rotacionar chave, uma semântica de claims e um ponto de auditoria.

## Arquitetura

```
                        Internet
                            │
                  ┌─────────▼──────────┐
                  │  API Gateway       │  HTTP API
                  └──┬──────────────┬──┘
        POST /auth   │              │  ANY /api/{proxy+}
                     │              │
          ┌──────────▼───────┐   ┌──▼──────────────────┐
          │  AuthFunction    │   │  AuthorizerFunction │
          │  (na VPC)        │   │  (fora da VPC)      │
          │                  │   │                     │
          │  1. valida CPF   │   │  valida assinatura  │
          │  2. consulta     │   │  do JWT, cache 300s │
          │     cliente      │   └──┬──────────────────┘
          │  3. emite JWT    │      │ isAuthorized
          └────────┬─────────┘      ▼
                   │            libera/bloqueia a rota
          ┌────────▼─────────┐
          │  RDS PostgreSQL  │  SELECT id, nome, status
          │  (subnet privada)│    FROM clientes WHERE cpf_cnpj = ?
          └──────────────────┘
```

Duas funções no mesmo projeto SAM:

| Função | Papel | Rede |
|---|---|---|
| `oficina-auth` | valida CPF, consulta cliente, emite JWT | dentro da VPC — precisa alcançar o RDS |
| `oficina-authorizer` | valida o JWT das rotas protegidas | fora da VPC — só verifica assinatura, cold start menor |

## Tecnologias

- **Java 21** · **AWS SAM** · arquitetura **arm64** (mais barata na tabela do Lambda)
- **JJWT 0.12** para assinatura e verificação HMAC-SHA256
- **PostgreSQL JDBC** — uma conexão por container, sem pool
- **AWS SDK v2 (SSM)** para o segredo e as credenciais
- JUnit 5, AssertJ, Mockito, JaCoCo

## Contrato da API

### `POST /auth`

```json
{ "cpf": "529.982.247-25" }
```

Aceita com ou sem máscara.

**200 OK**
```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 900 }
```

**Erros**

| Status | Código | Quando |
|---|---|---|
| 400 | `CPF_INVALIDO` | ausente, malformado ou dígito verificador incorreto |
| 404 | `CLIENTE_NAO_ENCONTRADO` | CPF válido, sem registro |
| 403 | `CLIENTE_INATIVO` | cliente com status `INATIVO` ou `BLOQUEADO` |
| 500 | `ERRO_INTERNO` | falha de banco ou de assinatura |

> **404 e 403 devolvem a mesma mensagem.** O código específico existe para o log; o corpo é genérico de propósito, para que ninguém use o endpoint para descobrir quais CPFs existem na base.

### Claims do token

| Claim | Conteúdo |
|---|---|
| `sub` | **UUID** do cliente — nunca o CPF, para não espalhá-lo pelos logs downstream |
| `cpf` | CPF normalizado, 11 dígitos |
| `nome` | nome do cliente |
| `role` | `CLIENTE` |
| `iss` | `oficina-auth` |
| `exp` | 900 segundos |

## Execução local

Pré-requisitos: JDK 21, Docker e AWS SAM CLI.

```bash
./mvnw verify          # 35 testes
sam validate --lint
sam build
```

Invocação local sem AWS — as variáveis de ambiente substituem o SSM:

```bash
sam local invoke AuthFunction \
  --event events/auth-cpf-valido.json \
  --env-vars env.local.json
```

`env.local.json` (não versionado):

```json
{
  "AuthFunction": {
    "JWT_SECRET": "chave-local-com-no-minimo-32-bytes-para-hmac-sha256",
    "DB_URL": "jdbc:postgresql://host.docker.internal:5432/oficina",
    "DB_USER": "oficina",
    "DB_PASSWORD": "oficina"
  }
}
```

Eventos prontos em `events/`: CPF válido, CPF inválido e authorizer sem token.

## Deploy

Automático, por ambiente: merge em `develop` publica `oficina-auth-lambda-staging` (homologação), merge em `main` publica `oficina-auth-lambda-prod` (produção, com aprovação no environment `prod`). Outras branches rodam só build e testes. Se o cluster do ambiente estiver desligado, o deploy é pulado com aviso, sem pedir aprovação. `develop` e `main` são protegidas, merge só por Pull Request com o check `Build & testes`.

**Ordem entre repositórios importa.** Este repo consome a rede publicada por `oficina-infra-k8s` e a migration `V6` aplicada por `oficina-api`:

```
1. oficina-infra-k8s   (rede, EKS, ECR, gateway sem rotas)
2. oficina-infra-db    (RDS)
3. oficina-api         (Flyway aplica V6 — cria clientes.status)
4. oficina-auth-lambda ← este repositório
5. oficina-infra-k8s   (segundo apply, agora com as rotas da Lambda)
```

Deploy manual:

```bash
sam deploy --stack-name oficina-auth-lambda-staging \
  --parameter-overrides Ambiente=staging \
      SubnetIds=/oficina/staging/private-subnet-ids \
      SecurityGroupId=/oficina/staging/lambda-security-group-id \
  --capabilities CAPABILITY_IAM --resolve-s3
```

Destroy pelo workflow **Destroy AWS**, **primeiro** entre os quatro repositórios: as ENIs da Lambda na VPC impedem o cluster de apagar subnet e security group. Local:

```bash
sam delete --stack-name oficina-auth-lambda-staging --no-prompts
```

## Contrato com os outros repositórios

Acoplamento único: **SSM Parameter Store**, sob `/oficina/<ambiente>/`.

| Parâmetro | Direção |
|---|---|
| `private-subnet-ids`, `lambda-security-group-id` | consome de `oficina-infra-k8s` |
| `jwt-secret` (SecureString) | consome — mesma origem que a aplicação usa |
| `db-endpoint`, `db-name`, `db-username`, `db-password` | consome de `oficina-infra-db` |
| `auth-lambda-arn`, `authorizer-lambda-arn` | **publica** para `oficina-infra-k8s` |

## Decisões e limitações registradas

- **Java em vez de Node.js** mantém a stack do time e é o que a Aula 06 de Serverless demonstra, ao custo de cold start de 1–3 s. Gatilho definido: se o p95 passar de 3 s, ativar SnapStart.
- **Concorrência reservada em 10.** A `db.t4g.micro` suporta ~85 conexões e a Lambda escala por invocação. Se precisar passar disso, a resposta é RDS Proxy, não aumentar o limite.
- **Validação de CPF é cópia** da regra que vive em `CpfCnpj`, no `oficina-api`. `CpfTest` espelha os mesmos casos-limite — é o que impede os dois validadores de divergirem enquanto não houver artefato compartilhado.
- **Autenticação por CPF sozinho é fraca.** CPF é identificador, não segredo. É o que o enunciado especifica e é o que está implementado; uma versão produtiva exigiria segundo fator. Mitigações no escopo: respostas indistinguíveis, throttling de 10 req/s na rota, token de 15 minutos sem refresh, e CPF nunca em log.
