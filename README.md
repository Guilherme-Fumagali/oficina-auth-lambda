# oficina-auth-lambda

Função serverless de autenticação por CPF do sistema de gestão de oficina mecânica, desenvolvida no Tech Challenge da PosTech FIAP (Arquitetura de Software).

| Repositório | Conteúdo |
|---|---|
| [tech-challenge-1](https://github.com/Guilherme-Fumagali/tech-challenge-1) | Aplicação oficina-api |
| **oficina-auth-lambda** | Autenticação por CPF (este repositório) |
| [oficina-infra-k8s](https://github.com/Guilherme-Fumagali/oficina-infra-k8s) | Rede, EKS, ECR, API Gateway e New Relic |
| [oficina-infra-db](https://github.com/Guilherme-Fumagali/oficina-infra-db) | RDS PostgreSQL |

## Propósito

Atende ao requisito de autenticação da Fase 3: validar o CPF, consultar a existência e o status do cliente ou do funcionário no banco de dados e emitir um JWT para consumo das APIs protegidas. A aplicação `oficina-api` apenas valida esse token e aplica as permissões de cada papel ([ADR-014](https://github.com/Guilherme-Fumagali/tech-challenge-1/blob/main/docs/tech-challenge-3/adrs/ADR-014-papeis-cliente-funcionario.md)).

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
                   │            libera ou bloqueia a rota
          ┌────────▼─────────┐
          │  RDS PostgreSQL  │  SELECT id, nome, status
          │  (subnet privada)│    FROM clientes WHERE cpf_cnpj = ?
          └──────────────────┘
```

| Função | Responsabilidade | Rede | Limites |
|---|---|---|---|
| `oficina-auth-<ambiente>` | atende `POST /auth` e `POST /auth/funcionarios`: valida o CPF, consulta o cliente ou o funcionário e emite o JWT | dentro da VPC, para acessar o RDS | 512 MB, 10 s, concorrência reservada 10 |
| `oficina-authorizer-<ambiente>` | valida o JWT das rotas protegidas | fora da VPC | 256 MB, 5 s |

O diagrama de sequência completo está em [`sequencia-autenticacao.md`](https://github.com/Guilherme-Fumagali/tech-challenge-1/blob/main/docs/tech-challenge-3/diagramas/sequencia-autenticacao.md).

## Tecnologias

- Java 21 e AWS SAM, arquitetura arm64
- JJWT 0.12 (assinatura e verificação HMAC-SHA256)
- Driver JDBC do PostgreSQL, com uma conexão por container de execução
- AWS SDK v2 para leitura de parâmetros do SSM
- JUnit 5, AssertJ, Mockito e JaCoCo

## Contrato da API

### `POST /auth` e `POST /auth/funcionarios`

```json
{ "cpf": "529.982.247-25" }
```

O CPF é aceito com ou sem máscara. `POST /auth` consulta a tabela `clientes` e emite token com papel `CLIENTE`; `POST /auth/funcionarios` consulta a tabela `funcionarios` e emite token com papel `FUNCIONARIO`. A mesma função atende as duas rotas e identifica o papel pelo `routeKey` do evento.

**200 OK**

```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 900 }
```

| Status | Código | Situação |
|---|---|---|
| 400 | `CPF_INVALIDO` | CPF ausente, malformado ou com dígito verificador incorreto; o banco não é consultado |
| 401 | `AUTENTICACAO_RECUSADA` | CPF válido sem cadastro, cliente `INATIVO` ou `BLOQUEADO`, ou funcionário `INATIVO` |
| 500 | `ERRO_INTERNO` | falha de banco ou de assinatura, sem detalhes na resposta |

CPF sem cadastro e cadastro sem permissão recebem exatamente a mesma resposta (status, cabeçalhos e corpo), para que o endpoint não permita descobrir quais CPFs estão cadastrados. O motivo (`nao_encontrado` ou `sem_permissao`) é registrado apenas no log, com o CPF limitado aos três últimos dígitos.

### Claims do token

| Claim | Conteúdo |
|---|---|
| `sub` | UUID do cliente ou do funcionário (o CPF não é usado como identificador, para não circular nos logs) |
| `cpf` | CPF normalizado, com 11 dígitos |
| `nome` | nome do cliente ou do funcionário |
| `role` | `CLIENTE` ou `FUNCIONARIO` |
| `iss` | `oficina-auth` |
| `exp` | 900 segundos após a emissão |

## Execução local

Pré-requisitos: JDK 21, Docker e AWS SAM CLI.

```bash
./mvnw verify
sam validate --lint
sam build
sam local invoke AuthFunction --event events/auth-cpf-valido.json --env-vars env.local.json
```

Com `JWT_SECRET` definido no ambiente, a configuração é lida das variáveis em vez do SSM. Exemplo de `env.local.json`, que não é versionado:

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

Eventos de exemplo em `events/`: CPF válido, CPF inválido e authorizer sem token.

## Testes

São 35 métodos de teste, que resultam em 44 casos executados por causa dos testes parametrizados. Cobertura: 77% de linhas e 76% de branches; o build falha abaixo de 75% e 70%, respectivamente.

| Classe | Métodos | O que verifica |
|---|---|---|
| `CpfTest` | 5 | CPF válido com e sem máscara, dígito verificador, tamanho incorreto, CNPJ recusado e mascaramento para log |
| `TokenTest` | 9 | token emitido aceito pelo validador, `sub` com UUID, papel vindo da identidade, recusa de outra chave, payload adulterado, token sem assinatura e entrada vazia, tamanho mínimo da chave, leitura do header `Bearer` |
| `AuthHandlerTest` | 11 | cliente ativo recebe token com papel `CLIENTE`; funcionário ativo recebe token com papel `FUNCIONARIO` sem consultar clientes; CPF malformado retorna 400 sem consultar o banco; corpo ausente; cliente ou funcionário inexistente e inativo recebem 401 com resposta idêntica e motivo apenas no log; falha de banco retorna 500 sem detalhes; CPF completo nunca aparece no log |
| `AuthorizerHandlerTest` | 5 | token válido autorizado com contexto; header com qualquer capitalização; ausência de header, chave diferente e esquema diferente de `Bearer` negados |
| `RepositoriosPostgresTest` | 5 | consultas reais em PostgreSQL 16 via Testcontainers: cliente ativo e bloqueado, funcionário ativo e inativo, CPF sem cadastro e falha de conexão |

```bash
./mvnw test      # requer Docker para o Testcontainers
./mvnw verify    # inclui o relatório JaCoCo e a verificação dos limites de cobertura
```

O schema usado em `RepositoriosPostgresTest` (`src/test/resources/schema-autenticacao.sql`) reproduz as colunas das tabelas `clientes` e `funcionarios` consultadas pela função, criadas pelas migrations da aplicação.

## CI/CD

Workflow: [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml).

| Job | Quando executa | O que faz |
|---|---|---|
| Build & testes | todo push, em qualquer branch | `./mvnw -B verify`: compila, executa todos os testes, inclusive contra PostgreSQL, verifica os limites de cobertura e publica o relatório JaCoCo |
| Conferir cluster | push em `develop` ou `main` | verifica se a rede do ambiente existe no SSM |
| Deploy | rede existente | `sam validate --lint`, `sam build` e `sam deploy` da stack do ambiente; confere os ARNs publicados no SSM |

| Branch | Stack | Aprovação |
|---|---|---|
| `develop` | `oficina-auth-lambda-staging` (homologação) | não |
| `main` | `oficina-auth-lambda-prod` (produção) | sim, no GitHub Environment `prod` |

As branches `develop` e `main` não aceitam push direto; o merge exige Pull Request com uma aprovação e o job **Build & testes** concluído com sucesso. Se o cluster do ambiente não existir, o deploy é ignorado com aviso, sem solicitar aprovação. A autenticação na AWS usa OIDC, com uma role exclusiva deste repositório; as roles criadas para as funções recebem o permissions boundary `oficina-lambda-boundary`.

Destruição: workflow **Destroy AWS**, executado antes dos demais repositórios, pois as interfaces de rede da função na VPC impedem a remoção das subnets e do security group do cluster.

## Ordem de provisionamento

Este repositório depende da rede criada pelo `oficina-infra-k8s` e do banco criado pelo `oficina-infra-db`:

```
1. oficina-infra-k8s   cluster, apply 1: rede, EKS e gateway sem as rotas da Lambda
2. oficina-infra-db    RDS
3. oficina-auth-lambda funções e publicação dos ARNs no SSM (este repositório)
4. oficina-infra-k8s   cluster, apply 2: rotas da Lambda e manifests da aplicação
```

A função é implantada no passo 3, mas só recebe chamadas após o passo 4, quando a rota é criada e a aplicação aplica a migration `V6` com a coluna `clientes.status`.

## Integração com os outros repositórios

A integração é feita pelo SSM Parameter Store, sob `/oficina/<ambiente>/`.

| Parâmetro | Direção |
|---|---|
| `private-subnet-ids`, `lambda-security-group-id` | consumido de `oficina-infra-k8s` |
| `jwt-secret` (SecureString) | consumido de `oficina-infra-k8s`, a mesma origem usada pela aplicação |
| `db-endpoint`, `db-name`, `db-username`, `db-password` | consumido de `oficina-infra-db` |
| tabelas `clientes` e `funcionarios` | lidas no banco; schema mantido pelas migrations da aplicação |
| `auth-lambda-arn`, `authorizer-lambda-arn` | publicado para `oficina-infra-k8s` |

## Decisões e limitações

- **Java em vez de Node.js:** mantém a stack da equipe e segue o exemplo da Aula 06 de Serverless, com cold start de 1 a 3 segundos. Se o p95 ultrapassar 3 segundos, a alternativa prevista é o SnapStart ([ADR-005](https://github.com/Guilherme-Fumagali/tech-challenge-1/blob/main/docs/tech-challenge-3/adrs/ADR-005-runtime-lambda-java-sam.md)).
- **Teto de 10 execuções simultâneas:** limita as conexões ao `db.t4g.micro`, que suporta cerca de 85. A conta utilizada tem limite total de 10 execuções simultâneas de Lambda, o que já impõe o teto e impede reservar concorrência (a AWS exige manter 10 execuções não reservadas). Por isso o parâmetro `ConcorrenciaReservada` do template tem padrão 0; com a cota ampliada, a reserva é definida no deploy com `ConcorrenciaReservada=10`. Para maior concorrência, a alternativa é o RDS Proxy.
- **Validação de CPF duplicada:** a regra também existe em `CpfCnpj`, na aplicação. `CpfTest` reproduz os mesmos casos-limite do teste da aplicação para evitar divergência (DT-08).
- **Autenticação apenas por CPF:** segue o enunciado. O CPF é um identificador, e não uma credencial secreta; as mitigações aplicadas são respostas indistinguíveis, throttling de 10 req/s na rota, token de 15 minutos sem refresh e CPF fora dos logs. O segundo fator está previsto para uma etapa futura (DT-06).
