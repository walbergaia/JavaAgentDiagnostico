# JavaAgentDiagnostico - Agente de Monitoramento Produtivo

##  visão geral

O **JavaAgentDiagnostico** é um agente de instrumentação Java (`javaagent`) projetado para monitoramento e observabilidade de aplicações em ambientes de produção. Ele opera com baixa intrusão, é altamente configurável e coleta uma ampla gama de métricas, exceções e dados de performance de SQL, enviando tudo para um endpoint REST para análise centralizada.

---

## 🚀 Funcionalidades Principais

-   **Métricas da JVM e do Sistema**: Coleta de uso de Heap, Non-Heap (Metaspace, CodeCache), estatísticas de Garbage Collection, threads, uso de CPU (processo e sistema) e memória (RAM e Swap).
-   **Captura de Exceções**: Registra todas as exceções não tratadas em qualquer thread da aplicação, incluindo tipo, mensagem e stack trace.
-   **Instrumentação de SQL**: Utiliza Byte Buddy para interceptar chamadas JDBC (`PreparedStatement`), capturando o template da query, tempo de execução e erros, sem expor dados sensíveis.
-   **Servidor de Configuração Dinâmica**: HTTP server integrado que permite consultar e alterar configurações em tempo de execução via API REST, sem necessidade de reiniciar a aplicação.
-   **Altamente Configurável**: Todo o comportamento do agente é controlado por um arquivo externo `agent.properties`, permitindo ativar ou desativar funcionalidades em tempo real sem alterar o código.
-   **Envio para API REST**: Consolida todos os dados coletados em um payload JSON e os envia periodicamente para um endpoint REST configurável, com suporte para autenticação via Bearer Token.
-   **Segurança**: Sanitiza queries SQL para remover parâmetros e limita a profundidade dos stack traces para evitar o vazamento de informações sensíveis.

---

## 🏗️ Arquitetura

O agente é anexado à JVM durante a inicialização e opera em segundo plano, coletando dados em uma thread separada para minimizar o impacto na aplicação principal.

```
[ Aplicação Java ]
        │
   -javaagent:diagnostic-agent.jar
        │
[ Agente Instrumentador (JavaAgentDiagnostico) ]
        │
  ┌───────────────────────────┐
  │  - Coletor de Métricas    │
  │  - Captura de Exceções    │
  │  - Instrumentação SQL     │
  │  - Configuração Externa   │
  └───────────────────────────┘
        │
[      Payload JSON     ]
        │
[    API REST Backend   ]
        │
[ Banco de Dados Relacional ]
```

---

## 🛠️ Como Construir

O projeto utiliza o Gradle. Para construir o agente e empacotar todas as dependências em um único arquivo JAR, execute um dos seguintes comandos:

### Windows:
```batch
.\gradlew build
```
ou execute o script de conveniência:
```batch
build.bat
```

### Linux/macOS:
```bash
./gradlew build
```
ou execute o script de conveniência:
```bash
./build.sh
```

O artefato final, `JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar`, será gerado no diretório `build/libs/`.

---

## ⚙️ Como Usar

Para monitorar sua aplicação, anexe o agente durante a inicialização usando a flag `-javaagent`.

1.  **Use** o JAR gerado em `build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar` (ou renomeie conforme preferir).
2.  **Coloque** o arquivo `agent.properties` no mesmo diretório da sua aplicação.
3.  **Inicie** sua aplicação com a flag:

```bash
java -javaagent:build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar -jar sua-aplicacao.jar
```

### Comandos Gradle Úteis:

- `.\gradlew build` - Compila e gera o JAR com todas as dependências
- `.\gradlew clean` - Limpa arquivos de build anteriores
- `.\gradlew shadowJar` - Gera apenas o fat JAR (sem executar testes)
- `.\gradlew tasks` - Lista todas as tasks disponíveis

O agente será carregado automaticamente e começará a enviar dados com base nas configurações do `agent.properties`.

---

## 📝 Configuração (`agent.properties`)

A seguir, a lista de todas as propriedades de configuração disponíveis:

| Chave                          | Tipo    | Descrição                                                 | Padrão  |
| ------------------------------ | ------- | --------------------------------------------------------- | ------- |
| `enabled`                      | boolean | Ativa ou desativa o agente completamente.                 | `true`  |
| `agent.name`                   | string  | Nome lógico do agente (ex: "agente-pagamentos").          | `java-agent` |
| `application.name`             | string  | Nome da aplicação monitorada (ex: "servico-de-faturas").  | `default-app` |
| `sampling.interval.ms`         | int     | Intervalo em milissegundos para a coleta de métricas.     | `15000` |
| `send.rest.enabled`            | boolean | Se `true`, envia os dados para a API REST.                | `true`  |
| `rest.endpoint.url`            | string  | URL do endpoint da API para onde os dados serão enviados. | -       |
| `auth.token`                   | string  | Token de autenticação (Bearer) para a API REST. Opcional. | -       |
| `enable.exception.capture`     | boolean | Ativa a captura de exceções não tratadas.                 | `true`  |
| `enable.sql.capture`           | boolean | Ativa o monitoramento de queries SQL.                     | `true`  |
| `sql.slow.threshold.ms`        | int     | Limiar em ms para considerar uma query como "lenta".      | `1000`  |
| `enable.deep.stack.analysis`   | boolean | Se `true`, captura o stack trace completo das exceções.   | `false` |
| `enable.gc.metrics`            | boolean | Ativa a coleta de métricas de Garbage Collection.         | `true`  |
| `enable.classloading.metrics`  | boolean | Ativa a coleta de métricas de carregamento de classes.    | `true`  |
| `enable.system.cpu.mem`        | boolean | Ativa a coleta de métricas de CPU e RAM do SO.            | `true`  |
| `config.server.enabled`        | boolean | Ativa o servidor HTTP de configuração dinâmica.           | `false` |
| `config.server.port`           | int     | Porta do servidor de configuração dinâmica.               | `8090`  |
| `config.server.auth.token`     | string  | Token de autenticação para o servidor de configuração.    | -       |
| `config.server.bind.address`   | string  | Endereço de bind do servidor (localhost por segurança).   | `localhost` |

---

## 🔧 Servidor de Configuração Dinâmica

O JavaAgentDiagnostico inclui um servidor HTTP integrado que permite alterar configurações em tempo de execução sem reiniciar a aplicação.

### Ativação

Para ativar o servidor de configuração, configure as seguintes propriedades no `agent.properties`:

```properties
config.server.enabled=true
config.server.port=8090
config.server.bind.address=localhost
# Opcional: token de autenticação
config.server.auth.token=seu-token-aqui
```

### Endpoints da API

#### `GET /health`
Retorna o status do servidor e do agente.

```bash
curl http://localhost:8090/health
```

**Resposta:**
```json
{
  "status": "UP",
  "timestamp": "2025-08-07T17:53:31.971Z",
  "configServer": "running",
  "agentEnabled": true
}
```

#### `GET /config`
Retorna todas as configurações atuais.

```bash
curl http://localhost:8090/config
# Com autenticação:
curl -H "Authorization: Bearer seu-token" http://localhost:8090/config
```

#### `POST /config`
Atualiza uma ou mais configurações dinamicamente.

```bash
# Desabilitar captura SQL
curl -X POST http://localhost:8090/config \
  -H "Content-Type: application/json" \
  -d '{"enable.sql.capture": "false"}'

# Com autenticação e múltiplas configurações
curl -X POST http://localhost:8090/config \
  -H "Authorization: Bearer seu-token" \
  -H "Content-Type: application/json" \
  -d '{"enabled": "true", "sampling.interval.ms": "10000"}'
```

**Resposta:**
```json
{
  "updated": {
    "enable.sql.capture": "false"
  }
}
```

#### `POST /config/reload`
Recarrega todas as configurações do arquivo `agent.properties`, removendo modificações dinâmicas.

```bash
curl -X POST http://localhost:8090/config/reload
# Com autenticação:
curl -X POST -H "Authorization: Bearer seu-token" http://localhost:8090/config/reload
```

### Segurança

- **Bind Local**: Por padrão, o servidor só aceita conexões de `localhost`
- **Autenticação Opcional**: Use `config.server.auth.token` para proteger os endpoints
- **Validação**: Valores são validados antes da aplicação
- **Auditoria**: Todas as mudanças são registradas nos logs

### Exemplos Práticos

```bash
# Verificar se o servidor está funcionando
curl http://localhost:8090/health

# Ver configurações atuais
curl http://localhost:8090/config

# Alterar intervalo de amostragem para 5 segundos
curl -X POST http://localhost:8090/config \
  -H "Content-Type: application/json" \
  -d '{"sampling.interval.ms": "5000"}'

# Desabilitar monitoramento de SQL temporariamente
curl -X POST http://localhost:8090/config \
  -H "Content-Type: application/json" \
  -d '{"enable.sql.capture": "false"}'

# Restaurar configurações do arquivo
curl -X POST http://localhost:8090/config/reload
```

---

## 🗃️ Esquema do Banco de Dados

O repositório inclui um script SQL em `/database-schema/V1__create_initial_tables.sql` para criar todas as tabelas necessárias em um banco de dados relacional (otimizado para PostgreSQL) para armazenar os dados recebidos pelo agente.